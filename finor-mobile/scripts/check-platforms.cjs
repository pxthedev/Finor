const assert = require("node:assert/strict");
const path = require("node:path");
const { createRequire } = require("node:module");
const expoRequire = createRequire(require.resolve("expo/package.json"));

async function main() {
  const { getPrebuildConfigAsync } = expoRequire("@expo/prebuild-config");
  const { compileModsAsync } = expoRequire(
    "@expo/config-plugins/build/plugins/mod-compiler.js",
  );
  const projectRoot = path.resolve(__dirname, "..");
  const { exp } = await getPrebuildConfigAsync(projectRoot, {
    platforms: ["ios", "android"],
  });
  await compileModsAsync(exp, {
    projectRoot,
    introspect: true,
    platforms: ["ios", "android"],
    assertMissingModProviders: false,
  });
  const ios = exp._internal.modResults.ios.infoPlist;
  assert.deepEqual([...exp.platforms].sort(), ["android", "ios"]);
  assert.equal(exp.ios.bundleIdentifier, "com.finor.app");
  assert.equal(ios.CFBundleDisplayName, "finor");
  assert.equal(exp.android.package, "com.finor.app");
  assert.ok(
    ios.NSPhotoLibraryUsageDescription,
    "iOS precisa da descrição para escolher a foto de perfil",
  );
  assert.ok(
    ios.CFBundleURLTypes.some((type) =>
      type.CFBundleURLSchemes?.includes("finor"),
    ),
    "iOS precisa abrir os links finor://",
  );
  assert.equal(
    ios.NSAppTransportSecurity.NSAllowsArbitraryLoads,
    process.env.FINOR_ALLOW_LOCAL_NETWORK === "1",
  );
  console.log(
    JSON.stringify(
      {
        platforms: exp.platforms,
        iosBundle: exp.ios.bundleIdentifier,
        androidPackage: exp.android.package,
        photoPermission: "OK",
        deepLinks: "OK",
        iosHttpOnlyInDevelopment: "OK",
      },
      null,
      2,
    ),
  );
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
