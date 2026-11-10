const { withMainActivity, withMainApplication } = require('expo/config-plugins');

// Keep Kotlin sources aligned with the namespace used to generate R and BuildConfig.
module.exports = function withAndroidNamespace(config) {
  const alignPackage = (config) => {
    const namespace = config.android?.package;
    if (!namespace) throw new Error('Defina android.package antes de configurar o Android.');
    config.modResults.contents = config.modResults.contents.replace(
      /^package\s+[\w.]+\s*;?\s*$/m,
      `package ${namespace}\n`,
    );
    return config;
  };
  config = withMainActivity(config, alignPackage);
  return withMainApplication(config, alignPackage);
};
