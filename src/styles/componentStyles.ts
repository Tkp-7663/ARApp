import { StyleSheet } from "react-native";

export const arSceneView = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: 'black',
	},
	sceneView: {
		flex: 1,
	},
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
		borderColor: 'red',
		backgroundColor: 'transparent',
	},
	confidenceText: {
		color: 'red',
		fontSize: 12,
		fontWeight: 'bold',
		backgroundColor: 'rgba(255,255,255,0.8)',
		paddingHorizontal: 4,
		paddingVertical: 2,
	},
	controlPanel: {
		position: 'absolute',
		bottom: 30,
		left: 20,
		right: 20,
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'center',
	},
	closeButton: {
		backgroundColor: 'rgba(255,0,0,0.8)',
		paddingHorizontal: 20,
		paddingVertical: 10,
		borderRadius: 20,
	},
	closeButtonText: {
		color: 'white',
		fontWeight: 'bold',
	},
	statusContainer: {
		alignItems: 'flex-end',
	},
	statusText: {
		color: 'white',
		fontSize: 12,
		backgroundColor: 'rgba(0,0,0,0.6)',
		paddingHorizontal: 8,
		paddingVertical: 4,
		borderRadius: 8,
		marginVertical: 2,
	},
});
