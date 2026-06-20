# UniView 

UniView is an Android application designed for university students to view, manage, and share their class schedules, exam timetables, academic results, and course descriptions—all in one centralized hub. 

*Note: This project is currently a **Work in Progress**.*

---

## 🚀 Current Status: Beta 1.2
The app is currently in active testing (`vbeta1.2`). If no major bugs are found during this testing phase, this build will be promoted to the first official stable release (`v1.0`). 

## ✨ Features
* **📅 Class & Exam Schedules:** Integrated viewing of academic timetables via iCal (.ics) integration.
* **📊 Academic Results:** Quick access to course grades and exam results.
* **📚 Course Directory:** Browse course lists complete with detailed descriptions and period breakdowns.
* **🔔 Smart Notifications:** 
    * Get notified 15 minutes before a lesson starts.
    * Receive alerts when updates to your hosted results or schedule are detected.
* **🖼️ Home Screen Widgets:** 
    * **Next Event:** A quick glance at your upcoming class or exam.
    * **Weekly Schedule:** Your entire week's planning directly on your home screen.
* **🌙 Dark Mode Support:** Full support for system-wide light and dark themes.
* **📶 Offline Access:** All fetched data is cached locally, ensuring you can check your room number even without a connection.

## 🛠️ Tech Stack & Architecture
* **Language:** Kotlin
* **UI Framework:** XML Views with ViewBinding
* **Background Work:** WorkManager for periodic data synchronization.
* **Data Sources (v1.x):**
  * Schedules are synced via iCal/ICS URLs (e.g., Google Calendar private links).
  * Course data and results are fetched from a shared JSON file.

---

## 🗺️ Roadmap & Future Plans (v2.0)
Planned features for the next major release include:
* **Two-Way Data Sync:** Transitioning the Google Drive storage to a custom Google Apps Script to allow editing data directly from the app interface.
* **Architecture Upgrades:** Refinement of the data sync pipeline to ensure it remains completely free and private.
* **Viewer Mode:** A restricted mode for sharing your schedule with others with optional access to your grades and without edit permissions.

---

## 🔧 Installation & Testing

### 🛠️ Cloning the Repository
To get a local copy of the project up and running:
1. Clone the repo:
   ```bash
   git clone https://github.com/Engineer-Matteo/UniView.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync and build the project.
4. Run the app on an emulator or a physical device.

### 📱 Installing the APK
1. Navigate to the [Releases](https://github.com/Engineer-Matteo/UniView/releases) section of this repository.
2. Download the latest `.apk` file (e.g., from the `vbeta1.2` tag).
3. On your Android device, enable **"Install from Unknown Sources"** in your security settings.
4. Open the downloaded file and follow the on-screen prompts to install.

### Using your personal schedule
1. If you do not have it yet, make a new calendar in Google Calendar with your classes and one with your exams.
2. Go to the settings of that calendar and go to the section **"Integrate calendar"**.
3. Copy the **"Secret address in iCal format"** and paste it into the app settings.
4. Press the save button.

### Adding your personal courses and grades
1. The app expects a download link for a JSON file. This is best achieved by hosting a `.json` file on Google Drive and using a direct download link.
2. Your link should look like: `https://drive.google.com/uc?export=download&id=YOUR_FILE_ID`
3. Paste that link in the app settings and press the save button.
4. **JSON Structure:** Ensure your JSON file follows this structure:
   ```json
   [
     {
        "courseName": "From Genome to Organism",
        "ects": "4",
        "score": "15",
        "year": "2027-2028",
        "program": "BA3 - BME",
        "semester": "1st semester",
        "description": "Human anatomy and biology",
        "professor": "prof. dr. ...",
        "location": "Health Campus",
        "partials": [
            {
                "name": "Theory exam",
                "weight": "0.5",
                "score": "16"
            },
            {
                "name": "",
                "weight": "",
                "score": ""
            },...]
    },...]
   ```
   *Note: If the `score` field is left blank, the course will appear in your Course Directory but will be hidden from the Results tab.*
