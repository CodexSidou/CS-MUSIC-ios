const { getDefaultConfig } = require('expo/metro-config');
const path = require('node:path');

const config = getDefaultConfig(__dirname);

const youtubeiJsBundle = path.join(
  __dirname,
  'node_modules',
  'youtubei.js',
  'bundle',
  'react-native.js'
);

const defaultResolver = config.resolver.resolveRequest;

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === 'youtubei.js') {
    return {
      filePath: youtubeiJsBundle,
      type: 'sourceFile',
    };
  }
  return defaultResolver
    ? defaultResolver(context, moduleName, platform)
    : context.resolveRequest(context, moduleName, platform);
};

module.exports = config;