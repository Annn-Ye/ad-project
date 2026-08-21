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

These screenshots are captured from working feature flows in [`material/Screenshots of working features`](<material/Screenshots of working features>).

### Candidate Android app

#### Career profile and resume

Candidates can keep their essential career information, profile photo and default resume in one place. The profile and resume are the foundation for applying to jobs and for receiving personalised recommendations.

![Candidate resume editor](material/Screenshots%20of%20working%20features/Manage%20Career%20Profile%20and%20Resume/resume%201.png)

#### Search and filter jobs

Candidates can search available roles and narrow results by employment type, workplace, location and minimum salary, making it easier to focus on relevant opportunities.

![Job filters](material/Screenshots%20of%20working%20features/Search%20Jobs/job%20filter.png)

#### Intelligent job recommendations

The recommendation flow surfaces roles based on the candidate's information. Each job can show a match score, matching skills and clear skill gaps so that the result is explainable rather than a black box.

![AI match analysis](material/Screenshots%20of%20working%20features/Receive%20Intelligent%20Job%20Recommendations/AI%20recommendation%201.png)

#### Save jobs and apply

Candidates can bookmark jobs for later and submit an application from the job detail page. The system protects against duplicate applications and records the resume snapshot used for the application.

| Save a role                                                                                               | Review details and apply                                                                                                |
| --------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| ![Saved jobs entry](material/Screenshots%20of%20working%20features/Save%20Jobs/save%20job%20entrance.png) | ![Job detail and application entry](material/Screenshots%20of%20working%20features/Apply%20for%20Jobs/job%20detail.png) |

#### Track applications and interviews

The **My applications** area groups application progress, interview activity and archived outcomes. From an application detail page, candidates can inspect the timeline and withdraw an application when appropriate.

| Application overview                                                                                                                          | Application detail and withdrawal                                                                                                                      |
| --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| ![Career dashboard and application overview](material/Screenshots%20of%20working%20features/View%20Career%20Dashboard/career%20dashboard.png) | ![Application detail and withdrawal](material/Screenshots%20of%20working%20features/Track%20and%20Withdraw%20Applications/withdraw%20applications.png) |

#### Communicate with recruiters

Messaging provides a direct candidate-recruiter conversation after an application or proactive outreach. It supports interview notifications as well as file and image attachments in the conversation thread.

![Conversation with interview notification and attachment](material/Screenshots%20of%20working%20features/Communicate%20with%20Recruiters/message%20detail.png)

#### Participate in the career community

The community provides searchable, categorised discussions for job seeking, recruiting, technical discussion and help. Candidates can publish posts with images, like and comment on posts, and message an author.

| Community feed                                                                                                               | Create a post                                                                                                                    |
| ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| ![Community feed](material/Screenshots%20of%20working%20features/Participate%20in%20Career%20Community/community%20page.png) | ![Create community post](material/Screenshots%20of%20working%20features/Participate%20in%20Career%20Community/create%20post.png) |

#### AI Agent for resume management

The AI Agent turns a natural-language request into a constrained plan, then shows the candidate a preview before a change is executed. This keeps resume operations auditable and prevents the Agent from acting without confirmation.

![AI Agent resume result](material/Screenshots%20of%20working%20features/AI%20Agent%20for%20Resume%20Management/agent%20summary.png)

#### Authentication, password reset and onboarding

Candidates and recruiters can register, sign in and reset a password using a verification code. New candidates then complete essential profile and resume information before using recommendation-dependent features.

| Recruiter sign-in | Successful sign-in |
| --- | --- |
| ![HireX Recruiter sign-in](material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/authentication%26log%20in.png) | ![HireX Recruiter dashboard after sign-in](material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/log%20in%20successfully.png) |

| Request a reset code | Verify the code and set a new password |
| --- | --- |
| ![HireX request password reset](material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/password%20reset%201.png) | ![HireX verify password reset code](material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/password%20reset%202.png) |

![HireX password reset verification email](material/Screenshots%20of%20working%20features/Authentication%20and%20Password%20Reset/email.jpg)

### Recruiter web workspace

Recruiters manage their company profile, create and publish jobs, review candidate applications, use Candidate Fit and AI-assisted ranking, communicate with candidates and schedule online, on-site or phone interviews. Google Calendar/Meet is an optional integration that becomes available after the recruiter connects a Google account.

#### Track recruitment activity and manage job postings

The Recruiter Dashboard summarises active roles, incoming applications, reviews, interviews and company-verification status. Recruiters can then create a job, manage its publication state and inspect its applicant count from the job-management workspace.

| Recruitment dashboard | Job management |
| --- | --- |
| ![Recruiter dashboard](material/Screenshots%20of%20working%20features/View%20Recruitment%20Dashboard/recruitment%20dashboard.png) | ![Recruiter job management](material/Screenshots%20of%20working%20features/Manage%20Job%20Postings/job%20page.png) |

![Recruiter creates a job posting](material/Screenshots%20of%20working%20features/Manage%20Job%20Postings/create%20job.png)

#### Manage company information and candidate outreach

Recruiters maintain the public company profile that candidates see on job details, and can open a direct conversation with a prospective candidate to support proactive outreach as well as application-related communication.

| Company profile | Recruiter-to-candidate conversation |
| --- | --- |
| ![Recruiter company profile](material/Screenshots%20of%20working%20features/Manage%20Company%20Profile/company%20profile.png) | ![Recruiter candidate conversation](material/Screenshots%20of%20working%20features/Communicate%20with%20Candidates/communicate%20details.png) |

#### Discover and screen candidates with AI assistance

Recruiters can browse recommended candidates for a role, review the ranking signal and message a promising candidate. The HR Agent can also screen candidates for a selected job and presents ranked results before any follow-up action is taken.

| Candidate discovery and ranking | HR Agent screening result |
| --- | --- |
| ![Recruiter candidate ranking](material/Screenshots%20of%20working%20features/Discover%20and%20Rank%20Candidates/discover%20%26%20rank%20candidates.png) | ![Recruiter Agent candidate screening](material/Screenshots%20of%20working%20features/Recruiter%20AI%20Screening%20and%20Interview%20Agent/use%20agent%20to%20find%20candidate.png) |

#### Manage the candidate pipeline

The application detail page gives recruiters one place to review the submitted resume, progress candidates through **Submitted → Review → Interview → Outcome**, inspect the AI Candidate Fit result and contact the candidate directly.

| Application review and progression | Schedule an interview |
| --- | --- |
| ![Recruiter application review](material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/Review%20application.png) | ![Recruiter interview scheduling](material/Screenshots%20of%20working%20features/Schedule%20Interviews/schedule%20interview%202.png) |

#### Interview and offer workflow

Recruiters can schedule an online Google Meet interview with a calendar invitation, open the generated meeting link, and record an application outcome such as an offer or rejection.

| Access the generated interview link | Record an offer outcome |
| --- | --- |
| ![Generated Google Meet interview link](material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/Visit%20interview%20meet%20link%20.png) | ![Recruiter makes an offer](material/Screenshots%20of%20working%20features/Manage%20Candidate%20Pipeline/Make%20offer.png) |

### Administrator web workspace

Administrators review company submissions, manage accounts, maintain company information and inspect audit records. Administrator registration is intentionally restricted; access is granted through the controlled bootstrap and authorisation flow.

| Restricted administrator sign-in | Account and access management |
| --- | --- |
| ![HireX Administrator sign-in](material/Screenshots%20of%20working%20features/Admin/1.png) | ![HireX Administrator accounts](material/Screenshots%20of%20working%20features/Admin/2.png) |

| Company verification and information maintenance | Traceable audit log |
| --- | --- |
| ![HireX Administrator company reviews](material/Screenshots%20of%20working%20features/Admin/3.png) | ![HireX Administrator audit log](material/Screenshots%20of%20working%20features/Admin/4.png) |

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
