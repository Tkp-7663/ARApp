import { StyleSheet, Dimensions } from 'react-native';

const { height: windowHeight } = Dimensions.get('screen');

export const arSceneView = StyleSheet.create({
	container: { flex: 1, backgroundColor: 'black' },
	sceneView: { flex: 1 },
	overlayContainer: {
		position: 'absolute',
		top: 0,
		left: 0,
		right: 0,
		bottom: 0,
	},
	boundingBox: {
		position: 'absolute',
		borderWidth: 2,
		borderColor: 'blue',
		justifyContent: 'center',
		alignItems: 'center',
	},
	confidenceText: {
		color: 'white',
		fontSize: 12,
		backgroundColor: 'rgba(0,0,0,0.5)',
		padding: 2,
	},
	// ปุ่ม Close AR ขยับขึ้น 20% ของความสูงจอ
	closeButton: {
		position: 'absolute',
		bottom: windowHeight * 0.2,
		left: '50%',
		transform: [{ translateX: -50 }],
		backgroundColor: 'red',
		paddingHorizontal: 16,
		paddingVertical: 8,
		borderRadius: 8,
	},
	closeButtonText: { color: 'white', fontWeight: 'bold' },
	// Status อยู่ล่างซ้าย ขยับขึ้น 10% ของความสูงจอ
	statusContainer: {
		position: 'absolute',
		bottom: windowHeight * 0.1,
		left: 10,
		alignItems: 'flex-start',
	},
	statusText: { color: 'white' },
});
