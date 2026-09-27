package com.adproject.conversation.application;

import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.conversation.api.ConversationDtos;
import com.adproject.conversation.api.ConversationDtos.Attachment;
import com.adproject.conversation.api.ConversationDtos.Detail;
import com.adproject.conversation.api.ConversationDtos.DetailResponse;
import com.adproject.conversation.api.ConversationDtos.ListResponse;
import com.adproject.conversation.api.ConversationDtos.Message;
import com.adproject.conversation.api.ConversationDtos.MessageListResponse;
import com.adproject.conversation.api.ConversationDtos.MessageResponse;
import com.adproject.conversation.api.ConversationDtos.PageMeta;
import com.adproject.conversation.api.ConversationDtos.Participant;
import com.adproject.conversation.api.ConversationDtos.ReadStateRequest;
import com.adproject.conversation.api.ConversationDtos.SendMessageRequest;
import com.adproject.conversation.api.ConversationDtos.Summary;
import com.adproject.conversation.domain.SenderType;
import com.adproject.conversation.domain.ConversationType;
import com.adproject.conversation.infrastructure.ConversationEntity;
import com.adproject.conversation.infrastructure.ConversationReadStateEntity;
import com.adproject.conversation.infrastructure.ConversationReadStateId;
import com.adproject.conversation.infrastructure.ConversationReadStateRepository;
import com.adproject.conversation.infrastructure.ConversationRepository;
import com.adproject.conversation.infrastructure.MessageAttachmentEntity;
import com.adproject.conversation.infrastructure.MessageAttachmentRepository;
import com.adproject.conversation.infrastructure.MessageEntity;
import com.adproject.conversation.infrastructure.MessageRepository;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.Visibility;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import jakarta.persistence.criteria.Subquery;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConversationService {
    private static final int MAX_BODY_LENGTH = 5000;
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final String DELIVERY_SENT = "SENT";

    private static final byte[] OLE_COMPOUND_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final String DOCX_CONTENT_TYPES_ENTRY = "[Content_Types].xml";
    private static final String DOCX_DOCUMENT_ENTRY = "word/document.xml";

    private static final Map<String, String> ATTACHMENT_CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg");

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final ConversationReadStateRepository readStates;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               MessageAttachmentRepository attachments,
                               ConversationReadStateRepository readStates, ApplicationRepository applications,
                               JobRepository jobs, UserRepository users, CandidateProfileRepository profiles,
                               CompanyRepository companies, CompanyMemberRepository members, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.attachments = attachments;
        this.readStates = readStates;
        this.applications = applications;
        this.jobs = jobs;
        this.users = users;
        this.profiles = profiles;
        this.companies = companies;
        this.members = members;
        this.clock = clock;
    }

    // ---- Candidate endpoints ----

    /**
     * 求职者只能读取自己作为参与方的会话；分页读取不会改变未读状态，已读状态必须由显式接口更新。
     */
    @Transactional(readOnly = true)
    public ListResponse listCandidate(AuthenticatedUser principal, int page, int pageSize) {
        requireCandidate(principal);
        var result = conversations.findByCandidateId(principal.userId(),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        var data = result.getContent().stream().map(c -> summary(c, principal.userId())).toList();
        return new ListResponse(data, new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public DetailResponse detailCandidate(AuthenticatedUser principal, String conversationId) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return new DetailResponse(detail(conversation, principal.userId()));
    }

    @Transactional(readOnly = true)
    public MessageListResponse listMessagesCandidate(AuthenticatedUser principal, String conversationId,
                                                     String before, int limit) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return listMessages(conversation, before, limit);
    }

    /**
     * 发送文本消息时以会话参与者身份和客户端幂等键双重约束，避免跨会话越权及网络重试造成重复消息。
     */
    @Transactional
    public MessageResponse sendCandidate(AuthenticatedUser principal, String conversationId, String idempotencyKey,
                                         SendMessageRequest request) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return send(conversation, principal, idempotencyKey, request);
    }

    @Transactional
    public void updateReadStateCandidate(AuthenticatedUser principal, String conversationId, ReadStateRequest request) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        updateReadState(conversation, principal.userId(), request);
    }

    /**
     * Opens the one Candidate-initiated inquiry for a public active job. It deliberately does
     * not require an application: this is the Candidate's pre-application contact entry point.
     * The existing uniqueness key includes type, job, candidate, company and recruiter, making
     * repeated taps safe without a new schema migration.
     */
    @Transactional
    public DetailResponse startCandidateInquiry(AuthenticatedUser principal, String jobId) {
        requireCandidate(principal);
        JobEntity job = jobs.findById(requireUuid(jobId, "jobId"))
                .filter(value -> value.getStatus() == JobStatus.ACTIVE)
                .filter(value -> value.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(this::notFound);
        String recruiterId = job.getOwnerId() != null ? job.getOwnerId() : job.getCreatedBy();
        users.findById(recruiterId).filter(value -> value.getRole() == UserRole.RECRUITER)
                .orElseThrow(this::notFound);
        ConversationEntity conversation = conversations
                .findByConversationTypeAndJobIdAndCandidateIdAndCompanyIdAndInitiatorRecruiterId(
                        ConversationType.CANDIDATE_INQUIRY, job.getId(), principal.userId(), job.getCompanyId(), recruiterId)
                .orElseGet(() -> conversations.save(ConversationEntity.candidateInquiry(UUID.randomUUID().toString(),
                        job.getId(), principal.userId(), job.getCompanyId(), recruiterId, clock.instant())));
        return new DetailResponse(detail(conversation, principal.userId()));
    }

    // ---- Recruiter endpoints ----

    /**
     * 招聘者会话仅限其所属公司。搜索、未读筛选和分页都在服务端做，前端不能通过本地过滤扩大可见范围。
     */
    @Transactional(readOnly = true)
    public ListResponse listRecruiter(AuthenticatedUser principal, String q, boolean unreadOnly, int page, int pageSize,
                                      String applicationId) {
        String companyId = requireCompany(principal);
        String application = optionalUuid(applicationId, "applicationId");
        Specification<ConversationEntity> specification = (root, query, cb) ->
                cb.equal(root.get("companyId"), companyId);
        if (application != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("applicationId"), application));
        }
        if (q != null && !q.isBlank()) {
            String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> {
                Subquery<String> candidates = query.subquery(String.class);
                var user = candidates.from(UserEntity.class);
                candidates.select(user.get("id")).where(cb.or(
                        cb.like(cb.lower(user.get("fullName")), value),
                        cb.like(cb.lower(user.get("email")), value)));
                return root.get("candidateId").in(candidates);
            });
        }
        var result = conversations.findAll(specification, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        List<Summary> data = result.getContent().stream().map(c -> summary(c, principal.userId())).toList();
        if (unreadOnly) {
            data = data.stream().filter(s -> s.unreadCount() > 0).toList();
        }
        return new ListResponse(data, new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public DetailResponse detailRecruiter(AuthenticatedUser principal, String conversationId) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return new DetailResponse(detail(conversation, principal.userId()));
    }

    @Transactional(readOnly = true)
    public MessageListResponse listMessagesRecruiter(AuthenticatedUser principal, String conversationId,
                                                     String before, int limit) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return listMessages(conversation, before, limit);
    }

    /**
     * 招聘者发送消息与候选人发送消息走同一核心写入流程；是否绑定 Google 账号不是站内消息的前置条件。
     */
    @Transactional
    public MessageResponse sendRecruiter(AuthenticatedUser principal, String conversationId, String idempotencyKey,
                                         SendMessageRequest request) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return send(conversation, principal, idempotencyKey, request);
    }

    @Transactional
    public MessageResponse sendCandidateAttachment(AuthenticatedUser principal, String conversationId,
                                                   String idempotencyKey, String clientMessageId, String body,
                                                   MultipartFile file) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return sendWithAttachment(conversation, principal, idempotencyKey, clientMessageId, body, file);
    }

    @Transactional
    public MessageResponse sendRecruiterAttachment(AuthenticatedUser principal, String conversationId,
                                                   String idempotencyKey, String clientMessageId, String body,
                                                   MultipartFile file) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return sendWithAttachment(conversation, principal, idempotencyKey, clientMessageId, body, file);
    }

    @Transactional(readOnly = true)
    public MessageAttachmentEntity downloadCandidateAttachment(AuthenticatedUser principal, String conversationId,
                                                               String messageId) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return downloadAttachment(conversation, messageId);
    }

    @Transactional(readOnly = true)
    public MessageAttachmentEntity downloadRecruiterAttachment(AuthenticatedUser principal, String conversationId,
                                                               String messageId) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return downloadAttachment(conversation, messageId);
    }

    @Transactional
    public void updateReadStateRecruiter(AuthenticatedUser principal, String conversationId, ReadStateRequest request) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        updateReadState(conversation, principal.userId(), request);
    }

    /**
     * 建立人才池主动联系会话。调用者已经由 Agent 端点验证为该次筛选结果中的候选人；这里仍再次
     * 校验公司、职位和候选人角色，防止这个领域服务被其他入口误用成任意陌生人私信接口。
     */
    @Transactional
    public String createRecruiterOutreach(AuthenticatedUser principal, String candidateId, String jobId) {
        String companyId = requireCompany(principal);
        JobEntity job = jobs.findById(requireUuid(jobId, "jobId"))
                .filter(value -> companyId.equals(value.getCompanyId()))
                .orElseThrow(this::notFound);
        UserEntity candidate = users.findById(requireUuid(candidateId, "candidateId"))
                .filter(value -> value.getRole() == UserRole.CANDIDATE)
                .orElseThrow(this::notFound);
        return conversations.findByConversationTypeAndJobIdAndCandidateIdAndCompanyIdAndInitiatorRecruiterId(
                        ConversationType.RECRUITER_OUTREACH, job.getId(), candidate.getId(), companyId,
                        principal.userId())
                .map(ConversationEntity::getId)
                .orElseGet(() -> conversations.save(ConversationEntity.recruiterOutreach(UUID.randomUUID().toString(),
                        job.getId(), candidate.getId(), companyId, principal.userId(), clock.instant())).getId());
    }

    // ---- Shared internals ----

    private MessageListResponse listMessages(ConversationEntity conversation, String before, int limit) {
        Instant beforeSentAt = null;
        String beforeId = null;
        if (before != null && !before.isBlank()) {
            MessageEntity cursor = messages.findById(before)
                    .filter(m -> m.getConversationId().equals(conversation.getId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                            "Request validation failed", Map.of("before", "must be a message in this conversation")));
            beforeSentAt = cursor.getSentAt();
            beforeId = cursor.getId();
        }
        List<MessageEntity> desc = messages.pageBefore(conversation.getId(), beforeSentAt, beforeId,
                PageRequest.of(0, limit + 1));
        boolean hasMore = desc.size() > limit;
        List<MessageEntity> page = hasMore ? desc.subList(0, limit) : desc;
        String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        List<Message> data = new ArrayList<>(toMessages(page));
        Collections.reverse(data);
        return new MessageListResponse(data, new ConversationDtos.MessageListMeta(nextCursor, hasMore));
    }

    /**
     * Appends a SYSTEM notice to the conversation of the given application, used
     * when a recruiter schedules an interview. The {@code noticeId} doubles as the
     * message id, idempotency key, and client message id, so retrying the same
     * interview write can never produce a duplicate. A missing conversation (an
     * application with no conversation yet) is skipped rather than treated as an
     * error, so the interview creation is never blocked by a notification gap.
     */
    @Transactional
    public void appendInterviewNotice(String applicationId, String recruiterId, String noticeId, String body) {
        // 面试通知使用 interviewId 作为幂等键，因此创建、重试或更新流程不会重复向候选人写同一条系统消息。
        if (messages.findById(noticeId).isPresent()) {
            return;
        }
        ConversationEntity conversation = conversations.findByApplicationId(applicationId).orElse(null);
        if (conversation == null) {
            return;
        }
        Instant now = clock.instant();
        String payloadHash = digest(conversation.getId() + "\n" + noticeId + "\n" + body);
        messages.save(new MessageEntity(noticeId, conversation.getId(), recruiterId, SenderType.SYSTEM,
                body, now, noticeId, noticeId, payloadHash));
        conversation.touch(now);
        conversations.flush();
    }

    private MessageResponse send(ConversationEntity conversation, AuthenticatedUser principal, String idempotencyKey,
                                 SendMessageRequest request) {
        String body = request.body() == null ? "" : request.body();
        if (body.isBlank()) {
            throw validation("body", "must not be blank");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw validation("body", "must be at most " + MAX_BODY_LENGTH + " characters");
        }
        String clientMessageId = requireUuid(request.clientMessageId(), "clientMessageId");
        String key = requireUuid(idempotencyKey, "Idempotency-Key");
        String payloadHash = digest(conversation.getId() + "\n" + clientMessageId + "\n" + body);

        var byKey = messages.findBySenderIdAndIdempotencyKey(principal.userId(), key);
        if (byKey.isPresent()) {
            if (byKey.get().getPayloadHash().equals(payloadHash)) {
                return new MessageResponse(message(byKey.get()));
            }
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used with a different request");
        }

        var byClient = messages.findByConversationIdAndClientMessageId(conversation.getId(), clientMessageId);
        if (byClient.isPresent()) {
            return new MessageResponse(message(byClient.get()));
        }

        if (isClosedApplicationConversation(conversation)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_CLOSED",
                    "This conversation is read-only because the application is no longer active");
        }

        Instant now = clock.instant();
        MessageEntity entity = messages.save(new MessageEntity(UUID.randomUUID().toString(), conversation.getId(),
                principal.userId(), senderType(principal.role()), body, now, clientMessageId, key, payloadHash));
        conversation.touch(now);
        conversations.flush();
        return new MessageResponse(message(entity));
    }

    private MessageResponse sendWithAttachment(ConversationEntity conversation, AuthenticatedUser principal,
                                               String idempotencyKey, String clientMessageId, String body,
                                               MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validation("file", "is required");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Unable to read the uploaded file");
        }
        AttachmentMeta meta = validateAttachment(file.getOriginalFilename(), content);

        String normalizedBody = body == null ? "" : body;
        if (normalizedBody.length() > MAX_BODY_LENGTH) {
            throw validation("body", "must be at most " + MAX_BODY_LENGTH + " characters");
        }
        String clientId = requireUuid(clientMessageId, "clientMessageId");
        String key = requireUuid(idempotencyKey, "Idempotency-Key");
        String contentHash = digest(content);
        String payloadHash = digest(conversation.getId() + "\n" + clientId + "\n" + normalizedBody
                + "\n" + meta.fileName() + "\n" + meta.sizeBytes() + "\n" + contentHash);

        var byKey = messages.findBySenderIdAndIdempotencyKey(principal.userId(), key);
        if (byKey.isPresent()) {
            if (byKey.get().getPayloadHash().equals(payloadHash)) {
                return new MessageResponse(message(byKey.get()));
            }
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used with a different request");
        }

        var byClient = messages.findByConversationIdAndClientMessageId(conversation.getId(), clientId);
        if (byClient.isPresent()) {
            return new MessageResponse(message(byClient.get()));
        }

        if (isClosedApplicationConversation(conversation)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_CLOSED",
                    "This conversation is read-only because the application is no longer active");
        }

        Instant now = clock.instant();
        MessageEntity entity = messages.save(new MessageEntity(UUID.randomUUID().toString(), conversation.getId(),
                principal.userId(), senderType(principal.role()), normalizedBody, now, clientId, key, payloadHash));
        messages.flush();
        attachments.save(new MessageAttachmentEntity(UUID.randomUUID().toString(), entity.getId(), meta.fileName(),
                meta.contentType(), meta.sizeBytes(), content, now));
        conversation.touch(now);
        conversations.flush();
        return new MessageResponse(message(entity));
    }

    private MessageAttachmentEntity downloadAttachment(ConversationEntity conversation, String messageId) {
        MessageEntity entity = messages.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversation.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Message not found"));
        return attachments.findByMessageId(entity.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Attachment not found"));
    }

    private AttachmentMeta validateAttachment(String originalFilename, byte[] content) {
        if (content.length == 0) {
            throw validation("file", "must not be empty");
        }
        if (content.length > MAX_ATTACHMENT_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "Attachment exceeds the 10 MB limit");
        }
        String fileName = sanitizeFileName(originalFilename);
        String extension = extensionOf(fileName);
        String contentType = ATTACHMENT_CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw validation("file", "must be one of: pdf, doc, docx, txt, png, jpg, jpeg");
        }
        if (!matchesMagicBytes(contentType, content)) {
            throw validation("file", "content does not match its file type");
        }
        return new AttachmentMeta(fileName, contentType, content.length);
    }

    private static String sanitizeFileName(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
        if (name.isEmpty()) {
            name = "attachment";
        }
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesMagicBytes(String contentType, byte[] content) {
        return switch (contentType) {
            case "application/pdf" -> startsWith(content, "%PDF-");
            case "application/msword" -> startsWith(content, OLE_COMPOUND_MAGIC);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> isDocx(content);
            case "text/plain" -> isPlainText(content);
            case "image/png" -> startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg" -> content.length >= 3 && (content[0] & 0xFF) == 0xFF
                    && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF;
            default -> false;
        };
    }

    /**
     * A DOCX is a ZIP archive; verify it carries a ZIP header and the two entries
     * every Office Open XML word document must contain. Any parse failure rejects it.
     */
    private static boolean isDocx(byte[] content) {
        if (!isZip(content)) {
            return false;
        }
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (DOCX_CONTENT_TYPES_ENTRY.equals(entry.getName())) {
                    hasContentTypes = true;
                } else if (DOCX_DOCUMENT_ENTRY.equals(entry.getName())) {
                    hasDocument = true;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return hasContentTypes && hasDocument;
    }

    private static boolean isZip(byte[] content) {
        if (content.length < 4) {
            return false;
        }
        int signature = (content[0] & 0xFF) | ((content[1] & 0xFF) << 8)
                | ((content[2] & 0xFF) << 16) | ((content[3] & 0xFF) << 24);
        // 0x04034B50 local file header, 0x06054B50 empty archive, 0x08074B50 spanned archive.
        return signature == 0x04034B50 || signature == 0x06054B50 || signature == 0x08074B50;
    }

    /**
     * A text attachment must be strictly decodable UTF-8 with no NUL byte or
     * unprintable control characters (newline, carriage return, and tab are allowed).
     */
    private static boolean isPlainText(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] content, String prefix) {
        return startsWith(content, prefix.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private record AttachmentMeta(String fileName, String contentType, long sizeBytes) {}

    private void updateReadState(ConversationEntity conversation, String userId, ReadStateRequest request) {
        String messageId = request.lastReadMessageId();
        if (messageId == null || messageId.isBlank()) {
            throw validation("lastReadMessageId", "is required");
        }
        MessageEntity target = messages.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversation.getId()))
                .orElseThrow(() -> validation("lastReadMessageId", "must be a message in this conversation"));
        Instant now = clock.instant();
        ConversationReadStateEntity state = readStates.findById(new ConversationReadStateId(conversation.getId(), userId))
                .orElse(new ConversationReadStateEntity(conversation.getId(), userId, target.getId(), now));
        state.update(target.getId(), now);
        readStates.save(state);
    }

    private Summary summary(ConversationEntity conversation, String viewerId) {
        boolean viewerIsCandidate = conversation.getCandidateId().equals(viewerId);
        return new Summary(conversation.getId(), conversation.getConversationType().name(), conversation.getApplicationId(), conversation.getJobId(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(),
                viewerIsCandidate ? recruiterParticipant(conversation) : candidateParticipant(conversation),
                lastMessage(conversation.getId()), unreadCount(conversation.getId(), viewerId),
                jobTitle(conversation.getJobId()));
    }

    private Detail detail(ConversationEntity conversation, String viewerId) {
        boolean viewerIsCandidate = conversation.getCandidateId().equals(viewerId);
        return new Detail(conversation.getId(), conversation.getConversationType().name(), conversation.getApplicationId(), conversation.getJobId(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(),
                viewerIsCandidate ? recruiterParticipant(conversation) : candidateParticipant(conversation), null);
    }

    private Participant recruiterParticipant(ConversationEntity conversation) {
        JobEntity job = jobs.findById(conversation.getJobId()).orElseThrow(this::notFound);
        String recruiterId = conversation.getInitiatorRecruiterId() != null ? conversation.getInitiatorRecruiterId()
                : job.getOwnerId() != null ? job.getOwnerId() : job.getCreatedBy();
        UserEntity recruiter = users.findById(recruiterId).orElseThrow(this::notFound);
        CompanyEntity company = companies.findById(conversation.getCompanyId()).orElseThrow(this::notFound);
        return new Participant(recruiter.getId(), recruiter.getFullName(), recruiter.getAvatarUrl(), null,
                company(company), false);
    }

    private Participant candidateParticipant(ConversationEntity conversation) {
        UserEntity candidate = users.findById(conversation.getCandidateId()).orElseThrow(this::notFound);
        var profile = profiles.findById(candidate.getId()).orElse(null);
        return new Participant(candidate.getId(), candidate.getFullName(), candidate.getAvatarUrl(),
                profile == null ? null : profile.getHeadline(), null, false);
    }

    private boolean isClosedApplicationConversation(ConversationEntity conversation) {
        if (conversation.getConversationType() != ConversationType.APPLICATION) {
            return false;
        }
        ApplicationEntity application = applications.findById(conversation.getApplicationId()).orElseThrow(this::notFound);
        return application.getStatus() == ApplicationStatus.REJECTED || application.getStatus() == ApplicationStatus.WITHDRAWN;
    }

    private Message lastMessage(String conversationId) {
        return messages.findLatest(conversationId, PageRequest.of(0, 1)).stream().findFirst()
                .map(this::message).orElse(null);
    }

    private long unreadCount(String conversationId, String userId) {
        Instant lastReadAt = readStates.findById(new ConversationReadStateId(conversationId, userId))
                .flatMap(s -> messages.findById(s.getLastReadMessageId()))
                .map(MessageEntity::getSentAt).orElse(null);
        return messages.countUnread(conversationId, userId, lastReadAt);
    }

    private String jobTitle(String jobId) {
        return jobs.findById(jobId).map(JobEntity::getTitle).orElseThrow(this::notFound);
    }

    private List<Message> toMessages(List<MessageEntity> entities) {
        List<String> ids = entities.stream().map(MessageEntity::getId).toList();
        Map<String, MessageAttachmentEntity> byMessageId = attachments.findByMessageIdIn(ids).stream()
                .collect(Collectors.toMap(MessageAttachmentEntity::getMessageId, a -> a));
        return entities.stream().map(entity -> toMessage(entity, byMessageId.get(entity.getId()))).toList();
    }

    private Message message(MessageEntity entity) {
        MessageAttachmentEntity attachment = attachments.findByMessageId(entity.getId()).orElse(null);
        return toMessage(entity, attachment);
    }

    private Message toMessage(MessageEntity entity, MessageAttachmentEntity attachment) {
        Attachment meta = attachment == null ? null
                : new Attachment(attachment.getId(), attachment.getFileName(), attachment.getSizeBytes(),
                        attachment.getContentType());
        return new Message(entity.getId(), entity.getConversationId(), entity.getBody(), entity.getSenderType().name(),
                entity.getSentAt(), entity.getClientMessageId(), DELIVERY_SENT, meta);
    }

    private static ConversationDtos.Company company(CompanyEntity company) {
        return new ConversationDtos.Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus().name(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private ConversationEntity requireCandidateConversation(AuthenticatedUser principal, String conversationId) {
        requireCandidate(principal);
        return conversations.findById(conversationId)
                .filter(c -> c.getCandidateId().equals(principal.userId()))
                .orElseThrow(this::notFound);
    }

    private ConversationEntity requireRecruiterConversation(AuthenticatedUser principal, String conversationId) {
        String companyId = requireCompany(principal);
        return conversations.findById(conversationId)
                .filter(c -> c.getCompanyId().equals(companyId))
                .orElseThrow(this::notFound);
    }

    private String requireCompany(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
        return members.findByUserId(principal.userId()).map(CompanyMemberEntity::getCompanyId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
    }

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static SenderType senderType(UserRole role) {
        return role == UserRole.CANDIDATE ? SenderType.CANDIDATE : SenderType.RECRUITER;
    }

    private static String optionalUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireUuid(value, field);
    }

    private static String requireUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                    Map.of(field, "is required"));
        }
        try { return UUID.fromString(value).toString(); }
        catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                    Map.of(field, "must be a UUID"));
        }
    }

    private static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static ApiException validation(String field, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                Map.of(field, detail));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Conversation not found");
    }
}
