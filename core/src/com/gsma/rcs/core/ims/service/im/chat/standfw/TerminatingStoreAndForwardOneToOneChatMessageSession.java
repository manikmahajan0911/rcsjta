/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.core.ims.service.im.chat.standfw;

import static com.gsma.rcs.utils.StringUtils.UTF8;

import com.gsma.rcs.core.ims.network.sip.SipMessageFactory;
import com.gsma.rcs.core.ims.network.sip.SipUtils;
import com.gsma.rcs.core.ims.protocol.msrp.MsrpEventListener;
import com.gsma.rcs.core.ims.protocol.msrp.MsrpException;
import com.gsma.rcs.core.ims.protocol.msrp.MsrpSession;
import com.gsma.rcs.core.ims.protocol.sdp.MediaAttribute;
import com.gsma.rcs.core.ims.protocol.sdp.MediaDescription;
import com.gsma.rcs.core.ims.protocol.sdp.SdpParser;
import com.gsma.rcs.core.ims.protocol.sdp.SdpUtils;
import com.gsma.rcs.core.ims.protocol.sip.SipDialogPath;
import com.gsma.rcs.core.ims.protocol.sip.SipException;
import com.gsma.rcs.core.ims.protocol.sip.SipPayloadException;
import com.gsma.rcs.core.ims.protocol.sip.SipRequest;
import com.gsma.rcs.core.ims.protocol.sip.SipResponse;
import com.gsma.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.gsma.rcs.core.ims.service.ImsSessionListener;
import com.gsma.rcs.core.ims.service.SessionTimerManager;
import com.gsma.rcs.core.ims.service.im.InstantMessagingService;
import com.gsma.rcs.core.ims.service.im.chat.ChatError;
import com.gsma.rcs.core.ims.service.im.chat.ChatUtils;
import com.gsma.rcs.core.ims.service.im.chat.OneToOneChatSession;
import com.gsma.rcs.core.ims.service.im.chat.OneToOneChatSessionListener;
import com.gsma.rcs.core.ims.service.im.chat.imdn.ImdnDocument;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileTransferUtils;
import com.gsma.rcs.provider.contact.ContactManager;
import com.gsma.rcs.provider.messaging.MessagingLog;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.PhoneUtils;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.contact.ContactId;

import java.io.IOException;
import java.util.Collection;
import java.util.Vector;

/**
 * Terminating Store & Forward session for one-one messages
 * 
 * @author jexa7410
 */
public class TerminatingStoreAndForwardOneToOneChatMessageSession extends OneToOneChatSession
        implements MsrpEventListener {
    /**
     * The logger
     */
    private final Logger mLogger = Logger
            .getLogger(TerminatingStoreAndForwardOneToOneChatMessageSession.class.getSimpleName());

    /**
     * Constructor
     * 
     * @param imService InstantMessagingService
     * @param invite Initial INVITE request
     * @param contact the remote ContactId
     * @param rcsSettings RCS settings
     * @param messagingLog Messaging log
     * @param timestamp Local timestamp for the session
     * @param contactManager
     * @throws SipPayloadException
     */
    public TerminatingStoreAndForwardOneToOneChatMessageSession(InstantMessagingService imService,
            SipRequest invite, ContactId contact, RcsSettings rcsSettings,
            MessagingLog messagingLog, long timestamp, ContactManager contactManager)
            throws SipPayloadException {
        super(imService, contact, PhoneUtils.formatContactIdToUri(contact), ChatUtils
                .getFirstMessage(invite, timestamp), rcsSettings, messagingLog, timestamp,
                contactManager);

        // Set feature tags
        setFeatureTags(ChatUtils.getSupportedFeatureTagsForChat(rcsSettings));

        // Create dialog path
        createTerminatingDialogPath(invite);

        // Set contribution ID
        String id = ChatUtils.getContributionId(invite);
        setContributionID(id);

        if (shouldBeAutoAccepted()) {
            setSessionAccepted();
        }
    }

    /**
     * Check is session should be auto accepted. This method should only be called once per session
     * 
     * @return true if one-to-one chat session should be auto accepted
     * @throws SipPayloadException
     */
    private boolean shouldBeAutoAccepted() throws SipPayloadException {
        /*
         * In case the invite contains a http file transfer info the chat session should be
         * auto-accepted so that the file transfer session can be started.
         */
        if (FileTransferUtils.getHttpFTInfo(getDialogPath().getInvite(), mRcsSettings) != null) {
            return true;
        }

        return mRcsSettings.isChatAutoAccepted();
    }

    /**
     * Background processing
     */
    public void run() {
        final boolean logActivated = mLogger.isActivated();
        try {
            if (logActivated) {
                mLogger.info("Initiate a store & forward session for messages");
            }
            SipDialogPath dialogPath = getDialogPath();

            /* Send message delivery report if requested */
            if (mImdnManager.isDeliveryDeliveredReportsEnabled()
                    && ChatUtils.isImdnDeliveredRequested(dialogPath.getInvite())) {
                /* Check notification disposition */
                String msgId = ChatUtils.getMessageId(dialogPath.getInvite());
                if (msgId != null) {
                    /* Send message delivery status via a SIP MESSAGE */
                    ContactId remote = getRemoteContact();
                    mImdnManager.sendMessageDeliveryStatusImmediately(remote.toString(), remote,
                            msgId, ImdnDocument.DELIVERY_STATUS_DELIVERED,
                            SipUtils.getRemoteInstanceID(dialogPath.getInvite()), getTimestamp());
                }
            }

            Collection<ImsSessionListener> listeners = getListeners();
            ContactId contact = getRemoteContact();
            /* Check if session should be auto-accepted once */
            if (isSessionAccepted()) {
                if (logActivated) {
                    mLogger.debug("Auto accept store and forward chat invitation");
                }

                for (ImsSessionListener listener : listeners) {
                    ((OneToOneChatSessionListener) listener).handleSessionAutoAccepted(contact);
                }
            } else {
                if (logActivated) {
                    mLogger.debug("Accept manually store and forward chat invitation");
                }

                for (ImsSessionListener listener : listeners) {
                    ((OneToOneChatSessionListener) listener).handleSessionInvited(contact);
                }

                send180Ringing(dialogPath.getInvite(), dialogPath.getLocalTag());

                InvitationStatus answer = waitInvitationAnswer();
                switch (answer) {
                    case INVITATION_REJECTED_DECLINE:
                        /* Intentional fall through */
                    case INVITATION_REJECTED_BUSY_HERE:
                        if (logActivated) {
                            mLogger.debug("Session has been rejected by user");
                        }
                        sendErrorResponse(dialogPath.getInvite(), dialogPath.getLocalTag(), answer);
                        removeSession();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionRejected(contact,
                                    TerminationReason.TERMINATION_BY_USER);
                        }
                        return;

                    case INVITATION_TIMEOUT:
                        if (logActivated) {
                            mLogger.debug("Session has been rejected on timeout");
                        }

                        /* Ringing period timeout */
                        send486Busy(dialogPath.getInvite(), dialogPath.getLocalTag());

                        removeSession();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionRejected(contact,
                                    TerminationReason.TERMINATION_BY_TIMEOUT);
                        }
                        return;

                    case INVITATION_REJECTED_BY_SYSTEM:
                        if (logActivated) {
                            mLogger.debug("Session has been aborted by system");
                        }
                        removeSession();
                        return;

                    case INVITATION_CANCELED:
                        if (logActivated) {
                            mLogger.debug("Session has been rejected by remote");
                        }

                        removeSession();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionRejected(contact,
                                    TerminationReason.TERMINATION_BY_REMOTE);
                        }
                        return;

                    case INVITATION_ACCEPTED:
                        setSessionAccepted();

                        for (ImsSessionListener listener : listeners) {
                            listener.handleSessionAccepted(contact);
                        }
                        break;

                    default:
                        throw new IllegalArgumentException(
                                "Unknown invitation answer in run; answer=".concat(String
                                        .valueOf(answer)));
                }
            }

            /* Parse the remote SDP part */
            final SipRequest invite = dialogPath.getInvite();
            String remoteSdp = invite.getSdpContent();
            SipUtils.assertContentIsNotNull(remoteSdp, invite);
            SdpParser parser = new SdpParser(remoteSdp.getBytes(UTF8));
            Vector<MediaDescription> media = parser.getMediaDescriptions();
            MediaDescription mediaDesc = media.elementAt(0);
            MediaAttribute attr1 = mediaDesc.getMediaAttribute("path");
            String remotePath = attr1.getValue();
            String remoteHost = SdpUtils.extractRemoteHost(parser.sessionDescription, mediaDesc);
            int remotePort = mediaDesc.port;

            /* Changed by Deutsche Telekom */
            String fingerprint = SdpUtils.extractFingerprint(parser, mediaDesc);

            /* Extract the "setup" parameter */
            String remoteSetup = "passive";
            MediaAttribute attr2 = mediaDesc.getMediaAttribute("setup");
            if (attr2 != null) {
                remoteSetup = attr2.getValue();
            }
            if (logActivated) {
                mLogger.debug("Remote setup attribute is ".concat(remoteSetup));
            }

            /* Set setup mode */
            String localSetup = createSetupAnswer(remoteSetup);
            if (logActivated) {
                mLogger.debug("Local setup attribute is ".concat(localSetup));
            }

            /* Set local port */
            int localMsrpPort;
            if (localSetup.equals("active")) {
                localMsrpPort = 9; /* See RFC4145, Page 4 */
            } else {
                localMsrpPort = getMsrpMgr().getLocalMsrpPort();
            }

            /* Build SDP part */
            String ipAddress = dialogPath.getSipStack().getLocalIpAddress();
            String sdp = SdpUtils.buildChatSDP(ipAddress, localMsrpPort, getMsrpMgr()
                    .getLocalSocketProtocol(), getAcceptTypes(), getWrappedTypes(), localSetup,
                    getMsrpMgr().getLocalMsrpPath(), getSdpDirection());

            /* Set the local SDP part in the dialog path */
            dialogPath.setLocalContent(sdp);

            /* Test if the session should be interrupted */
            if (isInterrupted()) {
                if (logActivated) {
                    mLogger.debug("Session has been interrupted: end of processing");
                }
                return;
            }

            /* Create a 200 OK response */
            if (logActivated) {
                mLogger.info("Send 200 OK");
            }
            SipResponse resp = SipMessageFactory.create200OkInviteResponse(dialogPath,
                    getFeatureTags(), sdp);
            dialogPath.setSigEstablished();

            /* Send response */
            SipTransactionContext ctx = getImsService().getImsModule().getSipManager()
                    .sendSipMessage(resp);

            /* Create the MSRP server session */
            if (localSetup.equals("passive")) {
                /* Passive mode: client wait a connection */
                MsrpSession session = getMsrpMgr().createMsrpServerSession(remotePath, this);
                session.setFailureReportOption(false);
                session.setSuccessReportOption(false);
                getMsrpMgr().openMsrpSession();
                /*
                 * Even if local setup is passive, an empty chunk must be sent to open the NAT and
                 * so enable the active endpoint to initiate a MSRP connection.
                 */
                sendEmptyDataChunk();
            }

            /* wait a response */
            getImsService().getImsModule().getSipManager().waitResponse(ctx);

            // Test if the session should be interrupted
            if (isInterrupted()) {
                if (mLogger.isActivated()) {
                    mLogger.debug("Session has been interrupted: end of processing");
                }
                return;
            }

            /* Analyze the received response */
            if (ctx.isSipAck()) {
                if (logActivated) {
                    mLogger.info("ACK request received");
                }
                dialogPath.setSessionEstablished();

                /* Create the MSRP client session */
                if (localSetup.equals("active")) {
                    /* Active mode: client should connect */
                    MsrpSession session = getMsrpMgr().createMsrpClientSession(remoteHost,
                            remotePort, remotePath, this, fingerprint);
                    session.setFailureReportOption(false);
                    session.setSuccessReportOption(false);
                    getMsrpMgr().openMsrpSession();
                    sendEmptyDataChunk();
                }

                for (ImsSessionListener listener : listeners) {
                    listener.handleSessionStarted(contact);
                }
                SessionTimerManager sessionTimerManager = getSessionTimerManager();
                if (sessionTimerManager.isSessionTimerActivated(resp)) {
                    sessionTimerManager.start(SessionTimerManager.UAS_ROLE,
                            dialogPath.getSessionExpireTime());
                }
                getActivityManager().start();

            } else {
                if (logActivated) {
                    mLogger.debug("No ACK received for INVITE");
                }

                /* No response received: timeout */
                handleError(new ChatError(ChatError.SEND_RESPONSE_FAILED));
            }
        } catch (MsrpException e) {
            handleError(new ChatError(ChatError.SEND_RESPONSE_FAILED, e));
        } catch (SipException e) {
            mLogger.error("Unable to send 200OK response!", e);
            handleError(new ChatError(ChatError.SEND_RESPONSE_FAILED, e));
        } catch (IOException e) {
            handleError(new ChatError(ChatError.SEND_RESPONSE_FAILED, e));
        } catch (RuntimeException e) {
            /*
             * Intentionally catch runtime exceptions as else it will abruptly end the thread and
             * eventually bring the whole system down, which is not intended.
             */
            mLogger.error("Failed initiating a store & forward session for messages!", e);
            handleError(new ChatError(ChatError.SEND_RESPONSE_FAILED, e));
        }
    }

    @Override
    public String getSdpDirection() {
        return SdpUtils.DIRECTION_RECVONLY;
    }

    @Override
    public boolean isInitiatedByRemote() {
        return true;
    }

    @Override
    public void startSession() {
        getImsService().getImsModule().getInstantMessagingService().addSession(this);
        start();
    }

    @Override
    public void removeSession() {
        getImsService().getImsModule().getInstantMessagingService().removeSession(this);
    }
}
