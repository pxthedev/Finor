import type { ExpoConfig, ConfigContext } from "expo/config";
const {
  validateBuildEnvironment,
  googleIosScheme,
} = require("./config/native-build.cjs");
export default ({ config }: ConfigContext): ExpoConfig => {
  validateBuildEnvironment(process.env);
  const iosClient = process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;
  const scheme = googleIosScheme(iosClient);
  const localNetwork = process.env.FINOR_ALLOW_LOCAL_NETWORK === "1";
  return {
    ...config,
    name: "finor",
    slug: "finor",
    platforms: ["android", "ios"],
    ios: {
      ...config.ios,
      bundleIdentifier: "com.finor.app",
      supportsTablet: true,
      requireFullScreen: true,
      infoPlist: {
        ...config.ios?.infoPlist,
        CFBundleDisplayName: "finor",
        CFBundleName: "finor",
        NSAppTransportSecurity: {
          NSAllowsArbitraryLoads: localNetwork,
          NSAllowsLocalNetworking: localNetwork,
        },
        ...(localNetwork
          ? {
              NSLocalNetworkUsageDescription:
                "O Finor usa a rede local para conectar ao servidor de desenvolvimento.",
            }
          : {}),
      },
    },
    plugins: [
      ...(config.plugins || []),
      "./plugins/with-windows-native-paths",
      "./plugins/with-android-namespace",
      [
        "expo-image-picker",
        {
          photosPermission:
            "Permita que o Finor acesse a foto escolhida para seu perfil.",
          cameraPermission: false,
          microphonePermission: false,
        },
      ],
      ...(scheme
        ? [
            [
              "@react-native-google-signin/google-signin",
              { iosUrlScheme: scheme },
            ] as [string, object],
          ]
        : []),
    ],
  };
};
