# VisionAid 📱🔊

VisionAid is an Android application developed in Kotlin aimed at assisting visually impaired users. The app uses the smartphone camera to detect objects in real time and provides audio feedback via text-to-speech.

---

## Project Overview

The goal of VisionAid is to enhance accessibility by enabling users with low or no vision to better understand their environment. Leveraging Google’s MediaPipe library for object detection, the app identifies objects around the user and announces them aloud.

---

## Key Features

- **Real-time object detection:** Continuously analyzes the camera feed to recognize objects.  
- **Audio feedback:** Uses Text-to-Speech (TTS) to inform users about detected objects.  
- **User-friendly interface:** Designed with accessibility in mind, focusing on simplicity and ease of use.

---

## Technologies Used

- **Kotlin:** Primary language for Android development.  
- **Android SDK:** Native tools for building the app.  
- **MediaPipe:** Machine learning framework for object detection.  
- **Text-to-Speech (TTS):** Converts text labels into spoken words for user feedback.

---

## How It Works

1. The user opens the app and grants camera permission.  
2. The app processes the camera input using MediaPipe to detect objects.  
3. Detected objects are announced through the device’s speakers.  
4. The detection and audio feedback loop runs continuously for real-time assistance.

---

## License

This project is developed for academic and educational purposes.

---
