const { withGradleProperties } = require("expo/config-plugins");

// Ninja bundled with CMake 3.22.1 cannot handle all generated Windows paths.
// Pin the SDK toolchain for every native module through Expo's root plugin.
module.exports = function withWindowsNativePaths(config) {
  // EAS Linux/macOS builders use their standard toolchains.
  if (process.platform !== "win32") return config;
  return withGradleProperties(config, (config) => {
    const properties = {
      "android.cmakeVersion": "3.31.6",
      "expo.android.cmakeObjectPathMax": "1024",
    };
    config.modResults = config.modResults.filter(
      (item) => item.type !== "property" || !(item.key in properties),
    );
    for (const [key, value] of Object.entries(properties)) {
      config.modResults.push({ type: "property", key, value });
    }
    return config;
  });
};
