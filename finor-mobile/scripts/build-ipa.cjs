const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const { createRequire } = require("node:module");
const expoRequire = createRequire(require.resolve("expo/package.json"));
const { validateBuildEnvironment } = require("../config/native-build.cjs");
const projectRoot = path.resolve(__dirname, "..");

function run(command, args, capture = false) {
  const result = spawnSync(command, args, {
    cwd: projectRoot,
    env: process.env,
    stdio: capture ? ["ignore", "pipe", "inherit"] : "inherit",
    encoding: "utf8",
  });
  if (result.error) throw result.error;
  if (result.status !== 0)
    throw new Error(`Falha ao executar ${command}. Consulte a saída acima.`);
  return result.stdout;
}

function main() {
  process.env.NODE_ENV = "production";
  expoRequire("@expo/env").load(projectRoot, { silent: true });
  const { exp } = expoRequire("@expo/config").getConfig(projectRoot);
  if (
    exp.ios?.bundleIdentifier !== "com.finor.app" ||
    exp.ios?.infoPlist?.CFBundleDisplayName !== "finor"
  )
    throw new Error(
      "Confira o nome finor e o Bundle ID com.finor.app em app.config.ts.",
    );
  const args = process.argv.slice(2);
  const unsigned = args.includes("--unsigned");
  const prepared = args.includes("--prepared");
  const methodArg = args.find((arg) => arg.startsWith("--method="));
  const method = methodArg
    ? methodArg.slice("--method=".length)
    : "release-testing";
  if (
    args.some(
      (arg) =>
        !["--check", "--unsigned", "--prepared", methodArg].includes(arg),
    ) ||
    (unsigned && methodArg) ||
    !["release-testing", "app-store-connect"].includes(method)
  )
    throw new Error(
      "Use npm run ipa:mac -- --method=release-testing ou --method=app-store-connect.",
    );
  if (args.includes("--check")) {
    let apiReady = true;
    try {
      validateBuildEnvironment({
        ...process.env,
        EAS_BUILD_PROFILE: "production",
      });
    } catch {
      apiReady = false;
    }
    console.log(
      JSON.stringify(
        {
          appName: "finor",
          bundleIdentifier: "com.finor.app",
          format: "ipa",
          macOS: process.platform === "darwin",
          appleTeamConfigured: /^[A-Z0-9]{10}$/.test(
            process.env.APPLE_TEAM_ID || "",
          ),
          publicApiConfigured: apiReady,
          cloudCommand: "npm run build:ipa",
          cloudArtifact:
            "finor-unsigned.ipa (requer assinatura pessoal antes de instalar)",
          signedCloudCommand: "npm run build:ipa:signed",
          freeMacDeviceCommand: "npm run ios -- --device",
          localMacCommand: "npm run ipa:mac",
          output: "build/ios/<data-hora>/export/finor.ipa",
        },
        null,
        2,
      ),
    );
    return;
  }
  if (process.platform !== "darwin")
    throw new Error(
      "A compilação local de IPA exige macOS com Xcode. No Windows, use npm run build:ipa (EAS com conta Expo e assinatura Apple).",
    );
  const team = process.env.APPLE_TEAM_ID || "";
  if (!unsigned && !/^[A-Z0-9]{10}$/.test(team))
    throw new Error(
      "Defina APPLE_TEAM_ID com o Team ID da sua conta Apple configurada no Xcode.",
    );
  validateBuildEnvironment({ ...process.env, EAS_BUILD_PROFILE: "production" });
  run("xcodebuild", ["-version"]);
  if (fs.existsSync(path.join(projectRoot, "ios/App/App.xcodeproj")))
    throw new Error(
      "A pasta ios contém um projeto Capacitor. Use uma cópia separada para esse projeto web antes de gerar o projeto nativo Expo.",
    );
  process.env.EAS_BUILD_PROFILE = "production";
  const expoCli = expoRequire.resolve("expo/bin/cli");
  if (!prepared)
    run(process.execPath, [
      expoCli,
      "prebuild",
      "--platform",
      "ios",
      "--no-clean",
    ]);
  const ios = path.join(projectRoot, "ios");
  const files = fs.readdirSync(ios);
  const workspaces = files.filter((file) => file.endsWith(".xcworkspace"));
  const projects = files.filter((file) => file.endsWith(".xcodeproj"));
  const candidates = workspaces.length ? workspaces : projects;
  if (candidates.length !== 1)
    throw new Error(
      "Não foi possível identificar um único projeto/workspace iOS.",
    );
  const target = [
    workspaces.length ? "-workspace" : "-project",
    path.join(ios, candidates[0]),
  ];
  const list = JSON.parse(
    run("xcodebuild", [...target, "-list", "-json"], true),
  );
  const schemes = (list.workspace || list.project).schemes;
  const scheme =
    process.env.IOS_SCHEME ||
    schemes.find((value) => value.toLowerCase() === "finor") ||
    (schemes.length === 1 ? schemes[0] : null);
  if (!scheme || !schemes.includes(scheme))
    throw new Error("Defina IOS_SCHEME com o scheme do app exibido no Xcode.");
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const output = path.join(projectRoot, "build", "ios", stamp);
  fs.mkdirSync(output, { recursive: true });
  const archive = path.join(output, "finor.xcarchive");
  const exportPath = path.join(output, "export");
  const options = path.join(output, "ExportOptions.plist");
  if (unsigned) {
    run("xcodebuild", [
      ...target,
      "-scheme",
      scheme,
      "-configuration",
      "Release",
      "-destination",
      "generic/platform=iOS",
      "-archivePath",
      archive,
      "CODE_SIGNING_ALLOWED=NO",
      "CODE_SIGNING_REQUIRED=NO",
      "CODE_SIGN_IDENTITY=",
      "archive",
    ]);
    const apps = fs
      .readdirSync(path.join(archive, "Products", "Applications"))
      .filter((file) => file.endsWith(".app"));
    if (apps.length !== 1)
      throw new Error("Não foi encontrado um único app iOS no archive.");
    const app = path.join(archive, "Products", "Applications", apps[0]);
    const identifier = run(
      "/usr/libexec/PlistBuddy",
      ["-c", "Print :CFBundleIdentifier", path.join(app, "Info.plist")],
      true,
    ).trim();
    if (identifier !== "com.finor.app")
      throw new Error("O app compilado não possui o Bundle ID com.finor.app.");
    const payload = path.join(output, "Payload");
    fs.mkdirSync(payload, { recursive: true });
    fs.mkdirSync(exportPath, { recursive: true });
    run("ditto", [app, path.join(payload, "finor.app")]);
    const result = path.join(exportPath, "finor-unsigned.ipa");
    run("ditto", ["-c", "-k", "--keepParent", payload, result]);
    console.log(
      `IPA sem assinatura gerado: ${result}. Assine com sua conta Apple antes de instalar.`,
    );
    return;
  }
  fs.writeFileSync(
    options,
    `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>method</key><string>${method}</string>
<key>teamID</key><string>${team}</string>
<key>signingStyle</key><string>automatic</string>
<key>destination</key><string>export</string>
<key>manageAppVersionAndBuildNumber</key><false/>
</dict></plist>\n`,
  );
  run("xcodebuild", [
    ...target,
    "-scheme",
    scheme,
    "-configuration",
    "Release",
    "-destination",
    "generic/platform=iOS",
    "-archivePath",
    archive,
    `DEVELOPMENT_TEAM=${team}`,
    "CODE_SIGN_STYLE=Automatic",
    "-allowProvisioningUpdates",
    "archive",
  ]);
  run("xcodebuild", [
    "-exportArchive",
    "-archivePath",
    archive,
    "-exportPath",
    exportPath,
    "-exportOptionsPlist",
    options,
    "-allowProvisioningUpdates",
  ]);
  const ipas = fs
    .readdirSync(exportPath)
    .filter((file) => file.endsWith(".ipa"));
  if (ipas.length !== 1)
    throw new Error("O Xcode não exportou um único arquivo IPA.");
  const result = path.join(exportPath, "finor.ipa");
  if (path.join(exportPath, ipas[0]) !== result)
    fs.renameSync(path.join(exportPath, ipas[0]), result);
  console.log(`IPA gerado: ${result}`);
}

try {
  main();
  process.exit(0);
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
