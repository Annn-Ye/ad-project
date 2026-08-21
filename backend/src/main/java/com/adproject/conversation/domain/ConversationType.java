package com.adproject.conversation.domain;

/** 区分由申请自动建立的会话与招聘者基于人才推荐发起的主动联系。 */
public enum ConversationType {
    APPLICATION,
    RECRUITER_OUTREACH,
    CANDIDATE_INQUIRY
}
