const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */

const defaultConfig = getDefaultConfig(__dirname);

const config = {
	resolver: {
		assetExts: [
			...defaultConfig.resolver.assetExts,
			'gltf',
			'glb',
			'bin',
			'obj',
			'mtl',
			'hdr',
			'vrx',
			'arobject',
			'gif',
			'PNG',
			'png',
			'JPG',
			'jpg',
			'jpeg',
			'usdz',
			'onnx',
		],
	},
};

module.exports = mergeConfig(defaultConfig, config);
