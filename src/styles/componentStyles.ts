import { StyleSheet } from "react-native";

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
	controlPanel: {
		position: 'absolute',
		bottom: 20,
		left: 0,
		right: 0,
		alignItems: 'center',
	},
	closeButton: {
		backgroundColor: 'red',
		paddingHorizontal: 16,
		paddingVertical: 8,
		borderRadius: 8,
		marginBottom: 8,
	},
	closeButtonText: { color: 'white', fontWeight: 'bold' },
	statusContainer: { alignItems: 'center' },
	statusText: { color: 'white' },
});
