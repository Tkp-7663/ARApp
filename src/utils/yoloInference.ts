import {NativeModules} from 'react-native';

interface DetectionResult {
  x: number;
  y: number;
  width: number;
  height: number;
  confidence: number;
  class: number;
}

const {OnnxRuntimeModule} = NativeModules;

export class YoloInference {
  private isInitialized = false;

  async initialize(): Promise<boolean> {
    try {
      this.isInitialized = await OnnxRuntimeModule.initializeModel();
      return this.isInitialized;
    } catch (error) {
      console.error('YOLO initialization failed:', error);
      return false;
    }
  }

  async detectWheels(frameData: string): Promise<DetectionResult[]> {
    if (!this.isInitialized) {
      await this.initialize();
    }

    try {
      const rawDetections = await OnnxRuntimeModule.runInference(frameData);
      return this.processDetections(rawDetections);
    } catch (error) {
      console.error('YOLO inference failed:', error);
      return [];
    }
  }

  private processDetections(rawDetections: number[][]): DetectionResult[] {
    const detections: DetectionResult[] = [];
    
    // YOLOv11n output format: [num_boxes, 6] -> [x, y, w, h, confidence, class]
    for (const detection of rawDetections) {
      if (detection.length >= 6 && detection[4] > 0.5) { // confidence threshold
        detections.push({
          x: detection[0] - detection[2] / 2, // convert center to top-left
          y: detection[1] - detection[3] / 2,
          width: detection[2],
          height: detection[3],
          confidence: detection[4],
          class: detection[5],
        });
      }
    }

    return detections;
  }
}

export const yoloInference = new YoloInference();
