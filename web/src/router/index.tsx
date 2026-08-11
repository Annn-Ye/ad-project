import {createBrowserRouter, Navigate} from 'react-router-dom';
import {AdminShell} from '../components/AdminShell';
import {AppShell} from '../components/AppShell';
import {AdminAuditPage} from '../pages/AdminAuditPage';
import {AdminAuthPage} from '../pages/AdminAuthPage';
import {AdminCompaniesPage} from '../pages/AdminCompaniesPage';
import {AdminModerationPage} from '../pages/AdminModerationPage';
import {AdminUsersPage} from '../pages/AdminUsersPage';
import {ApplicationDetailPage} from '../pages/ApplicationDetailPage';
import {ApplicationsPage} from '../pages/ApplicationsPage';
import {AuthPage} from '../pages/AuthPage';
import {DashboardPage} from '../pages/DashboardPage';
import {JobDetailPage} from '../pages/JobDetailPage';
import {JobFormPage} from '../pages/JobFormPage';
import {JobsPage} from '../pages/JobsPage';
import {MessagesPage} from '../pages/MessagesPage';
import {NotFoundPage} from '../pages/NotFoundPage';
import {ResumeReviewPage} from '../pages/ResumeReviewPage';

export const router = createBrowserRouter([
  {path: '/', element: <Navigate to="/recruiter/sign-in" replace/>},
  {path: '/recruiter/sign-in', element: <AuthPage mode="signin"/>},
  {path: '/recruiter/create-account', element: <AuthPage mode="register"/>},
  {path: '/recruiter', element: <AppShell/>, children: [
    {path: 'dashboard', element: <DashboardPage/>},
    {path: 'jobs', element: <JobsPage/>},
    {path: 'jobs/new', element: <JobFormPage/>},
    {path: 'jobs/:jobId/edit', element: <JobFormPage/>},
    {path: 'jobs/:jobId', element: <JobDetailPage/>},
    {path: 'applications', element: <ApplicationsPage/>},
    {path: 'applications/:applicationId', element: <ApplicationDetailPage/>},
    {path: 'applications/:applicationId/review', element: <ResumeReviewPage/>},
    {path: 'messages', element: <MessagesPage/>},
    {path: 'messages/:conversationId', element: <MessagesPage/>},
  ]},
  {path: '/admin/sign-in', element: <AdminAuthPage/>},
  {path: '/admin', element: <AdminShell/>, children: [
    {index: true, element: <Navigate to="/admin/users" replace/>},
    {path: 'users', element: <AdminUsersPage/>},
    {path: 'company-reviews', element: <AdminCompaniesPage/>},
    {path: 'moderation', element: <AdminModerationPage/>},
    {path: 'audit-log', element: <AdminAuditPage/>},
  ]},
  {path: '*', element: <NotFoundPage/>},
]);
