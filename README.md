# HireX

> An intelligent recruitment platform that helps candidates discover suitable roles and helps recruiters manage the hiring journey from job creation to interview.

## Team responsibilities

| Module                                  | Delivery scope                                                                                                                                 | Owners                                       |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------- |
| Android frontend                        | Jetpack Compose candidate UI: onboarding, profile/resume, jobs, applications, messaging, community and Candidate Agent                         | Huang Yichen, Ye Zhian                       |
| Web frontend                            | React recruiter and administrator UI: profiles, jobs, applications, messaging, community, Agent and administration                             | Huang Yichen, Ye Zhian                       |
| Candidate onboarding and profile        | Android registration, sign-in, onboarding questionnaire, profile, avatar, resume and job preferences                                           | Huang Yichen, Ye Zhian                       |
| Candidate job journey                   | Android jobs, search and filters, saved jobs, job details, applications and application tracking                                               | Huang Yichen, Ye Zhian                       |
| Recruiter company and jobs              | Recruiter profile, company-review guidance, dashboard, job drafts, job publishing, editing and closing                                         | Huang Yichen, Ye Zhian                       |
| Recruiter hiring workflow               | Applications, candidate details and resumes, status changes, Candidate Fit, interview scheduling and management                                | Huang Yichen, Wang Qiuye                     |
| Messaging and notifications             | Cross-platform conversations, unread indicators, attachments/images, interview notifications and direct entry from a candidate profile or post | Huang Yichen, Wang Qiuye                     |
| Community                               | Candidate and recruiter post feeds, search, categories, posts with images, likes, comments and author messaging                                | Chen Hongbing                                |
| Administration and company governance   | Administrator access, company reviews, accounts, company data maintenance, audit logs and pagination                                           | Wang Qiuye                                   |
| ML recommendation service               | Job recommendations, candidate ranking, scores, explanations, cold-start handling and fallback integration                                     | Yan Bohao                                    |
| ML training and evaluation              | Data cleaning, Top-300 retrieval, teacher labels, HGB, embeddings, collaborative filtering and hybrid-model evaluation                         | Chen Jiale                                   |
| AI Agent                                | Candidate and recruiter planning, previews, explicit confirmation and execution records                                                        | Liu Ruinan                                   |
| DevSecOps                               | CI/CD security gates, SAST, dependency and container scanning, DAST, and deployment-security configuration                                     | Bian Haifan                                  |
| Deployment and quality                  | Docker, cloud configuration, CI, demo data, OpenAPI, regression tests and project documentation                                                | Bian Haifan                                  |
| Project analysis and delivery artefacts | Class diagrams, sequence diagrams, project status reports and sprint backlog                                                                   | Chen Hongbing,Yan Bohao,Chen Jiale           |
| Product design artefacts                | Class diagrams, sequence diagrams and Figma design                                                                                             | Chen Jiale,Chen Hongbing,Yan Bohao，Ye Zhian |

## Project overview

HireX is a three-role recruitment platform:

- **Candidates** use the Android app to build a career profile, receive job recommendations, search and save roles, apply, track outcomes, communicate with recruiters and participate in the community.
- **Recruiters** use the web workspace to manage company information and jobs, review applications, contact candidates, schedule interviews and use AI-assisted candidate ranking.
- **Administrators** use the web workspace to govern accounts and companies, review company submissions, maintain company information and inspect audit activity.

The primary end-to-end flow is:

```text
Recruiter creates and publishes a job
        ↓
Candidate discovers the job through search or recommendation
        ↓
Candidate views details and applies with a resume
        ↓
Recruiter reviews the application, candidate fit and ranking
        ↓
Recruiter updates the outcome or schedules an interview
        ↓
Candidate receives the update and continues the conversation
```

## Key features

### Candidate Android app

- Secure registration, sign-in, password reset and first-time onboarding.
- Editable career profile, avatar, resume and job preferences.
- Intelligent job recommendations with match scores and skill-gap explanations.
- Job search, employment-type and preference filters, saved jobs and detailed role/company/recruiter views.
- Application submission, duplicate-application protection, tracking, withdrawal and interview updates.
- Recruiter messaging with unread status, files and image attachments.
- Community posts, categories, search, images, likes, comments and direct author messaging.
- An AI Agent that plans authorised profile/resume actions, shows a preview and executes only after explicit confirmation.

### Recruiter web workspace

- Recruiter profile and company-review status guidance.
- Dashboard, job draft creation, publishing, editing and closing.
- Application pipeline, candidate profile/resume review and status transitions.
- Candidate Fit and AI-assisted ranking for applicants and recommended talent.
- Proactive candidate outreach, message threads and attachment support.
- Online, on-site and phone interview scheduling; Google Calendar/Meet is available when a recruiter has completed the optional Google integration.

### Administrator web workspace

- Restricted administrator access without public admin registration.
- Company reviews, account status management and company information maintenance.
- Audit logs and clear paginated views for operational review.

### ML and AI Agent

- A Python ML service trains and serves job recommendation and candidate-ranking models; the Spring Boot API provides graceful fallback when the service is unavailable.
- The AI Agent uses a plan-preview-confirm-execute workflow. The planner does not directly access the database or gain privileges beyond the signed-in user.

## Feature walkthroughs

Every functional screenshot in [`material/Screenshots of working features`](<material/Screenshots of working features>) is shown below. Compact previews keep the complete feature set easy to scan.

### Candidate Android app

<h3>1. Career profile and resume</h3>

<p>Maintain the profile, photo and default resume used for applications and recommendations.</p>

<p><img src="material/Screenshots%20of%20working%20features/Manage%20Career%20Profile%20and%20Resume/edit%20profile%201.png" alt="Candidate profile editor" width="200" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Career%20Profile%20and%20Resume/edit%20profile%202.png" alt="Candidate profile fields" width="200" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Career%20Profile%20and%20Resume/resume%201.png" alt="Candidate resume editor" width="200" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Career%20Profile%20and%20Resume/resume%202.png" alt="Candidate resume details" width="200" /></p>

<h3>2. Job preferences and exclusions</h3>

<p>Set preferences that shape recommended roles and filters.</p>

<p><img src="material/Screenshots%20of%20working%20features/Manage%20Job%20Preferences%20and%20Exclusions/filter%20jobs.png" alt="Candidate job preferences" width="200" /></p>

<h3>3. Search, filter and view jobs</h3>

<p>Search roles, apply structured filters and inspect the full job description.</p>

<p><img src="material/Screenshots%20of%20working%20features/Search%20Jobs/search%20job.png" alt="Job search" width="200" /> <img src="material/Screenshots%20of%20working%20features/Search%20Jobs/job%20filter.png" alt="Job filters" width="200" /> <img src="material/Screenshots%20of%20working%20features/Search%20Jobs/job%20detail.png" alt="Job detail" width="200" /></p>

<h3>4. Intelligent job recommendations</h3>

<p>Show match scores, matching skills and explainable skill gaps.</p>

<p><img src="material/Screenshots%20of%20working%20features/Receive%20Intelligent%20Job%20Recommendations/AI%20recommendation%201.png" alt="AI recommendation" width="200" /> <img src="material/Screenshots%20of%20working%20features/Receive%20Intelligent%20Job%20Recommendations/AI%20recommendation%202.png" alt="AI recommendation analysis" width="200" /></p>

<h3>5. Save jobs</h3>

<p>Bookmark roles and revisit the saved-job list or a saved role's detail.</p>

<p><img src="material/Screenshots%20of%20working%20features/Save%20Jobs/save%20job%20%EF%BC%88click%20star%EF%BC%89.png" alt="Save a job" width="200" /> <img src="material/Screenshots%20of%20working%20features/Save%20Jobs/save%20job%20details.png" alt="Saved job detail" width="200" /> <img src="material/Screenshots%20of%20working%20features/Save%20Jobs/save%20job%20entrance.png" alt="Saved jobs entry" width="200" /></p>

<h3>6. Apply for jobs</h3>

<p>Review the role, confirm the default-resume submission and receive a recorded result.</p>

<p><img src="material/Screenshots%20of%20working%20features/Apply%20for%20Jobs/job%20detail.png" alt="Apply from job detail" width="200" /> <img src="material/Screenshots%20of%20working%20features/Apply%20for%20Jobs/confirm%20application.png" alt="Confirm application" width="200" /> <img src="material/Screenshots%20of%20working%20features/Apply%20for%20Jobs/application%20submitted.png" alt="Application submitted" width="200" /></p>

<h3>7. Track and withdraw applications</h3>

<p>View application states, interview activity and, where permitted, withdraw a submission.</p>

<p><img src="material/Screenshots%20of%20working%20features/View%20Career%20Dashboard/career%20dashboard.png" alt="Career dashboard" width="200" /> <img src="material/Screenshots%20of%20working%20features/Track%20and%20Withdraw%20Applications/track%20applications%201.png" alt="Application tracking" width="200" /> <img src="material/Screenshots%20of%20working%20features/Track%20and%20Withdraw%20Applications/track%20applications%202.png" alt="Application progress" width="200" /> <img src="material/Screenshots%20of%20working%20features/Track%20and%20Withdraw%20Applications/track%20applications%203.png" alt="Application interview detail" width="200" /> <img src="material/Screenshots%20of%20working%20features/Track%20and%20Withdraw%20Applications/withdraw%20applications.png" alt="Withdraw application" width="200" /></p>

<h3>8. Communicate with recruiters</h3>

<p>Open a conversation and exchange interview notifications, files and images.</p>

<p><img src="material/Screenshots%20of%20working%20features/Communicate%20with%20Recruiters/message%20entrance.png" alt="Candidate message entry" width="200" /> <img src="material/Screenshots%20of%20working%20features/Communicate%20with%20Recruiters/message%20detail.png" alt="Candidate recruiter conversation" width="200" /></p>

<h3>9. Participate in the career community</h3>

<p>Browse, publish, like and comment on categorised career discussions.</p>

<p><img src="material/Screenshots%20of%20working%20features/Participate%20in%20Career%20Community/community%20page.png" alt="Community feed" width="200" /> <img src="material/Screenshots%20of%20working%20features/Participate%20in%20Career%20Community/create%20post.png" alt="Create community post" width="200" /> <img src="material/Screenshots%20of%20working%20features/Participate%20in%20Career%20Community/like%26commit%20post.png" alt="Like and comment on post" width="200" /></p>

<h3>10. Candidate AI Agent</h3>

<p>Turn a natural-language request into a previewed, confirmed resume-management change.</p>

<p><img src="material/Screenshots%20of%20working%20features/AI%20Agent%20for%20Resume%20Management/agent%201.png" alt="Candidate Agent request" width="200" /> <img src="material/Screenshots%20of%20working%20features/AI%20Agent%20for%20Resume%20Management/agent%202.png" alt="Candidate Agent preview" width="200" /> <img src="material/Screenshots%20of%20working%20features/AI%20Agent%20for%20Resume%20Management/agent%203.png" alt="Candidate Agent confirmation" width="200" /> <img src="material/Screenshots%20of%20working%20features/AI%20Agent%20for%20Resume%20Management/agent%20summary.png" alt="Candidate Agent result" width="200" /></p>

<h3>11. Authentication, password reset and onboarding</h3>

<p>Register, sign in and reset a password through an emailed verification code before completing essential candidate information.</p>

<p><img src="material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/authentication%26log%20in.png" alt="Recruiter sign in" width="200" /> <img src="material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/log%20in%20successfully.png" alt="Successful sign in" width="200" /> <img src="material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/password%20reset%201.png" alt="Request password reset" width="200" /> <img src="material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/password%20reset%202.png" alt="Verify password reset code" width="200" /> <img src="material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/email.jpg" alt="Password reset verification email" width="200" /></p>

### Recruiter web workspace

<h3>12. Recruitment dashboard</h3>

<p>Summarise active roles, applications, reviews, interviews and company verification.</p>

<p><img src="material/Screenshots%20of%20working%20features/View%20Recruitment%20Dashboard/recruitment%20dashboard.png" alt="Recruiter dashboard" width="300" /></p>

<h3>13. Manage job postings</h3>

<p>Create roles, inspect their detail and control their publication state and applicants.</p>

<p><img src="material/Screenshots%20of%20working%20features/Manage%20Job%20Postings/create%20job.png" alt="Create job" width="240" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Job%20Postings/job%20page.png" alt="Job management" width="240" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Job%20Postings/job%20details.png" alt="Job posting detail" width="240" /></p>

<h3>14. Manage company profile</h3>

<p>Maintain the company information candidates see with job listings.</p>

<p><img src="material/Screenshots%20of%20working%20features/Manage%20Company%20Profile/company%20profile.png" alt="Recruiter company profile" width="300" /></p>

<h3>15. Communicate with candidates</h3>

<p>Begin and manage proactive candidate conversations.</p>

<p><img src="material/Screenshots%20of%20working%20features/Communicate%20with%20Candidates/communicate%20entrance.png" alt="Recruiter message entry" width="240" /> <img src="material/Screenshots%20of%20working%20features/Communicate%20with%20Candidates/communicate%20details.png" alt="Recruiter candidate conversation" width="240" /></p>

<h3>16. Discover and rank candidates</h3>

<p>Review recommended candidates for a role and start outreach.</p>

<p><img src="material/Screenshots%20of%20working%20features/Discover%20and%20Rank%20Candidates/discover%20%26%20rank%20candidates.png" alt="Candidate discovery and ranking" width="300" /></p>

<h3>17. Recruiter AI screening and interview Agent</h3>

<p>Start an HR Agent conversation and receive ranked candidate screening results before action.</p>

<p><img src="material/Screenshots%20of%20working%20features/Recruiter%20AI%20Screening%20and%20Interview%20Agent/AI%20agent%20start%20page.png" alt="Recruiter Agent start page" width="240" /> <img src="material/Screenshots%20of%20working%20features/Recruiter%20AI%20Screening%20and%20Interview%20Agent/use%20agent%20to%20find%20candidate.png" alt="Recruiter Agent screening" width="240" /></p>

<h3>18. Review applications and manage the candidate pipeline</h3>

<p>Review resumes and Candidate Fit, then progress candidates through Submitted, Review, Interview and Outcome.</p>

<p><img src="material/Screenshots%20of%20working%20features/Review%20Applications/application%20page.png" alt="Recruiter applications list" width="200" /> <img src="material/Screenshots%20of%20working%20features/Review%20Applications/application%20details.png" alt="Recruiter application detail" width="200" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/View%20application%20pipeline.png" alt="Application pipeline" width="200" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/Review%20application.png" alt="Review application" width="200" /></p>

<h3>19. Schedule interviews and record outcomes</h3>

<p>Schedule online Google Meet, on-site or phone interviews, access the meeting link and record an offer or rejection.</p>

<p><img src="material/Screenshots%20of%20working%20features/Schedule%20Interviews/schedule%20interview%201.png" alt="Schedule interview" width="180" /> <img src="material/Screenshots%20of%20working%20features/Schedule%20Interviews/schedule%20interview%202.png" alt="Google Meet interview schedule" width="180" /> <img src="material/Screenshots%20of%20working%20features/Schedule%20Interviews/schedule%20interview%203.png" alt="Scheduled interview" width="180" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/Schedule%20interview.png" alt="Schedule interview from pipeline" width="180" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/Visit%20interview%20meet%20link%20.png" alt="Google Meet link" width="180" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/Make%20offer.png" alt="Make offer" width="180" /></p>

### Administrator web workspace

<h3>20. Restricted administrator access and account management</h3>

<p>Use controlled administrator sign-in, manage accounts and grant platform access.</p>

<p><img src="material/Screenshots%20of%20working%20features/Admin/1.png" alt="Administrator sign in" width="220" /> <img src="material/Screenshots%20of%20working%20features/Admin/2.png" alt="Administrator accounts" width="220" /> <img src="material/Screenshots%20of%20working%20features/Manage%20Users/manage%20users.png" alt="Administrator manages users" width="220" /></p>

<h3>21. Review companies and maintain company information</h3>

<p>Inspect company submissions and record an approval or rejection decision.</p>

<p><img src="material/Screenshots%20of%20working%20features/Admin/3.png" alt="Administrator company reviews" width="260" /> <img src="material/Screenshots%20of%20working%20features/Review%20Companies/review%20companies.png" alt="Review company" width="260" /></p>

<h3>22. Review the administrator audit log</h3>

<p>Trace administrative actions, affected records, reasons and request identifiers.</p>

<p><img src="material/Screenshots%20of%20working%20features/Admin/4.png" alt="Administrator audit log" width="260" /> <img src="material/Screenshots%20of%20working%20features/View%20Administrator%20Audit%20Log/audit%20log.png" alt="Audit log entries" width="260" /></p>

## Architecture and technology

```text
Android Candidate App ─┐
                       ├── Spring Boot API ── MySQL + Flyway
React Recruiter/Admin ─┘          │
                                  ├── Python ML service
                                  └── Python Agent planner
```

| Layer                   | Technology                                                                      |
| ----------------------- | ------------------------------------------------------------------------------- |
| Candidate client        | Kotlin, Jetpack Compose and Retrofit                                            |
| Recruiter/Admin clients | React, TypeScript, Vite and TanStack Query                                      |
| Core API                | Java 21 and Spring Boot                                                         |
| Database                | MySQL 8 with Flyway migrations                                                  |
| ML                      | Python training and inference service                                           |
| Agent                   | Python/LangGraph planning service; Spring Boot owns authorisation and execution |
| Deployment              | Docker Compose, Nginx and Ansible                                               |

Spring Boot is the only public business API. The Android and web clients do not access the database, ML service or Agent planner directly.

## Local development

Prerequisites: Java 21, Maven, Node.js, Android Studio/JDK 21, Docker and MySQL 8 (or the provided Docker services).

1. Copy [`.env.example`](.env.example) to an untracked `.env` file and replace placeholders with local values. Never commit passwords, API keys, OAuth secrets or production connection strings.

2. Start MySQL, then run the backend:

   ```bash
   cd backend
   mvn spring-boot:run
   ```

3. Start the web workspace:

   ```bash
   cd web
   npm install
   npm run dev
   ```

4. Build the Android candidate app:

   ```bash
   cd android
   ./gradlew assembleDebug
   ```

Useful checks:

```bash
cd backend && mvn test
cd web && npm run typecheck && npm test
cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Documentation

- [Project architecture and business flows (Chinese)](docs/PROJECT_ARCHITECTURE_AND_BUSINESS_FLOWS.zh-CN.md)
- [Product requirements](docs/product-requirements.md)
- [User flows](docs/user-flows.md)
- [System architecture](docs/architecture.md)
- [Database design](docs/database-design.md)
- [API design](docs/api-design.md)
- [OpenAPI contract](docs/openapi-v1.yaml)
- [Authorization rules](docs/permissions.md)
- [Testing plan](docs/testing-plan.md)
- [Agent design](docs/agent-design.md)

## Contributing

Please read [AGENTS.md](AGENTS.md) before making changes. Keep API contracts and Flyway migrations aligned, preserve existing work from other contributors, and do not commit secrets or generated local environment files.
