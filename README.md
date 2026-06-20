# UniView 

UniView is an Android application designed for university students to view, manage, and share their class schedules, exam timetables, academic results, and course descriptions—all in one centralized hub. 

*Note: This project is currently a **Work in Progress**.*

---

## 🚀 Current Status: Beta 1.2
The app is currently in active testing (`vbeta1.2`). If no major bugs are found during this testing phase, this build will be promoted to the first official stable release (`v1.0`). 

## ✨ Features
* **Class & Exam Schedules:** Integrated viewing of academic timetables.
* **Academic Results:** Quick access to course grades.
* **Course Directory:** Browse course lists complete with detailed descriptions.

## 🛠️ Tech Stack & Architecture
* **Language:** Kotlin
* **UI Framework:** XML Views
* **Data Sources (v1.x):**
  * Schedules are synced via private Google Calendar links.
  * Course data and results are fetched from a shared JSON file hosted on Google Drive.

---

## 🗺️ Roadmap & Future Plans (v2.0)
Planned features for the next major release include:
* **Two-Way Data Sync:** Transitioning the Google Drive storage to a custom Google Apps Script. This will allow users to securely update and write data back to the JSON file directly from the app interface.
* **Architecture Upgrades:** Refinement of the data sync pipeline to ensure it remains completely free, private, and secure.
* **Different User Modes:** A seperate mode (to be selected upon installation) for students and viewers (if you want to share your shedule with parents or others), making it so only the student can edit the data while viewers can only view the data.

---

## 🔧 Installation & Testing
*(Optional: Add brief instructions here later on how to clone the repo or install the beta APK if you decide to share it).*
