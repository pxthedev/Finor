const { getDefaultConfig } = require("expo/metro-config");
const config = getDefaultConfig(__dirname);
config.resolver.blockList = [/\.modules-sdk54[\\/].*/, /dist-check[\\/].*/];
module.exports = config;
