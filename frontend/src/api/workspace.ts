// Compatibility facade for legacy callers. New code imports the domain API modules directly.
export * from "./legacyWorkspace";
export { fetchDashboardSummary } from "./dashboard";
export { fetchCalendarEvents } from "./calendar";
export {
  acceptSpaceInvitation,
  createSpace,
  createSpaceInvitation,
  declineSpaceInvitation,
  deleteSpace,
  fetchSpaceDetail,
  fetchSpaceMembers,
  fetchSpaces,
  removeSpaceMember,
  transferSpaceOwner,
  updateSpace,
  updateSpaceMemberRole
} from "./spaces";
export { chatMeetingAi, chatProjectAi, fetchProjectAiHistory } from "./ai";
export { createInstantMeeting, createMeeting, deleteMeeting, fetchMeetingDetail, fetchMeetings, updateMeeting } from "./meetings";
export { fetchMeetingDialogue, startMeetingTranscription, stopMeetingTranscription } from "./transcripts";
export {
  addMeetingParticipant,
  approveMeetingJoinRequest,
  createMeetingJoinRequest,
  fetchMeetingJoinRequests,
  fetchMeetingParticipants,
  rejectMeetingJoinRequest,
  updateMeetingParticipant
} from "./meetingAccess";
export {
  archiveDomainTerm,
  createDomainTerm,
  explainMeetingTerm,
  fetchDomainTerms,
  updateDomainTerm
} from "./terms";
export {
  createProjectKnowledge,
  deleteProjectKnowledge,
  fetchProjectKnowledge,
  fetchProjectKnowledgeDetail,
  updateProjectKnowledge
} from "./knowledge";
export {
  confirmTaskCandidate,
  createTask,
  deleteTask,
  dismissTaskCandidate,
  extractTaskCandidates,
  fetchTaskCandidates,
  fetchTasks,
  updateTask
} from "./tasks";
export {
  confirmMeetingReport,
  downloadMeetingReport,
  editMeetingReportWithAi,
  fetchMeetingReportDetail,
  fetchMeetingReports,
  generateReportCandidate,
  restoreMeetingReport,
  updateMeetingReport
} from "./reports";
