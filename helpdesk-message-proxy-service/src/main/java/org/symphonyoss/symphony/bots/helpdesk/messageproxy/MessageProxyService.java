package org.symphonyoss.symphony.bots.helpdesk.messageproxy;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.client.exceptions.MessagesException;
import org.symphonyoss.client.exceptions.RoomException;
import org.symphonyoss.client.model.Chat;
import org.symphonyoss.client.model.Room;
import org.symphonyoss.client.services.MessageListener;
import org.symphonyoss.symphony.bots.ai.HelpDeskAiSession;
import org.symphonyoss.symphony.bots.ai.HelpDeskAiSessionContext;
import org.symphonyoss.symphony.bots.ai.conversation.ProxyConversation;
import org.symphonyoss.symphony.bots.ai.impl.SymphonyAiChatListener;
import org.symphonyoss.symphony.bots.ai.impl.SymphonyAiSessionKey;
import org.symphonyoss.symphony.bots.ai.model.AiConversation;
import org.symphonyoss.symphony.bots.ai.model.AiSessionKey;
import org.symphonyoss.symphony.bots.helpdesk.messageproxy.model.ClaimEntityTemplateData;
import org.symphonyoss.symphony.bots.helpdesk.messageproxy.model.ClaimMessageTemplateData;
import org.symphonyoss.symphony.bots.helpdesk.messageproxy.model.MessageProxy;
import org.symphonyoss.symphony.bots.helpdesk.messageproxy.model.MessageProxyServiceSession;
import org.symphonyoss.symphony.bots.helpdesk.service.client.MembershipClient;
import org.symphonyoss.symphony.bots.helpdesk.service.client.TicketClient;
import org.symphonyoss.symphony.bots.helpdesk.service.model.Membership;
import org.symphonyoss.symphony.bots.helpdesk.service.model.Ticket;
import org.symphonyoss.symphony.bots.utility.template.MessageTemplate;
import org.symphonyoss.symphony.clients.model.SymMessage;
import org.symphonyoss.symphony.clients.model.SymRoomAttributes;
import org.symphonyoss.symphony.clients.model.SymUser;
import org.symphonyoss.symphony.pod.model.Stream;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by nick.tarsillo on 9/26/17.
 * The message proxy service handles the proxying of messages between clients and agents.
 */
public class MessageProxyService implements MessageListener {
  private static final Logger LOG = LoggerFactory.getLogger(MessageProxyService.class);

  public static final int TICKET_ID_LENGTH = 7;

  private Map<String, MessageProxy> proxyMap = new HashMap<>();

  private MessageProxyServiceSession session;

  public MessageProxyService(MessageProxyServiceSession messageProxyServiceSession) {
    this.session = messageProxyServiceSession;
  }

  /**
   * On message:
   *    Check if membership exits, if not, create membership. (CLIENT)
   *    Get AiConversation for the user that sent the message.
   *
   *    If the member is an agent, the agent is talking in a client service room
   *      and a proxy has not been created yet, create a new proxy conversation for ticket.
   *
   *    If the member is an agent, the agent is talking in a client service room
   *      and a proxy has already been created, add the agent to the proxy.
   *
   *    If the member is a client, and a ticket has not been created, create a new ticket and
   *      proxy.
   *
   *    If the member is a client, and ticket exists but not a proxy conversation, create a new proxy.
   *
   *    If the member is a client, and the ticket and proxy exists with the unserviced state,
   *      update the ticket transcript.
   *
   * @param symMessage the message to proxy.
   */
  @Override
  public void onMessage(SymMessage symMessage) {
    String userId = symMessage.getFromUserId().toString();
    String streamId = symMessage.getStreamId();

    Membership membership = session.getMembershipClient().getMembership(userId);
    if(membership == null) {
      membership = session.getMembershipClient().newMembership(userId,
          MembershipClient.MembershipType.CLIENT);
    }

    AiSessionKey symphonyAiSessionKey = session.getHelpDeskAi().getSessionKey(userId, streamId);
    HelpDeskAiSessionContext aiSessionContext =
        (HelpDeskAiSessionContext) session.getHelpDeskAi().getSessionContext(symphonyAiSessionKey);
    AiConversation aiConversation = session.getHelpDeskAi().getConversation(symphonyAiSessionKey);

    Ticket ticket;
    if(MembershipClient.MembershipType.AGENT.equals(membership.getType())) {
      ticket = session.getTicketClient().getTicketByServiceStreamId(streamId);
      if(ticket != null && !proxyMap.containsKey(ticket.getId())) {
        createAgentProxy(ticket, aiSessionContext);
      } else if(ticket != null && aiConversation == null) {
        addAgentToProxy(ticket, aiSessionContext);
      }
    } else {
      ticket = session.getTicketClient().getTicketByClientStreamId(streamId);
      if (ticket == null) {
        String ticketId = RandomStringUtils.randomAlphanumeric(TICKET_ID_LENGTH).toUpperCase();
        ticket = session.getTicketClient().createTicket(
            ticketId, newServiceStream(ticketId, streamId), streamId, symMessage.getMessageText());
        sendClaimTicketMessage(ticketId);
        createClientProxy(ticket, aiSessionContext);
      } else if(!proxyMap.containsKey(ticket.getId())) {
        createClientProxy(ticket, aiSessionContext);
      } else if(TicketClient.TicketStateType.UNSERVICED.equals(ticket.getState())) {
        ticket.addTranscriptItem(symMessage.getMessage());
        session.getTicketClient().updateTicket(ticket);
      }
    }
  }

  /**
   * If a agent talks in a client service room, but a proxy mapping does not exist, create a new
   * mapping based on the ticket.
   * @param ticket the ticket to base the mapping on.
   */
  private void createAgentProxy(Ticket ticket, HelpDeskAiSessionContext aiSessionContext) {
    aiSessionContext.setGroupId(session.getGroupId());
    aiSessionContext.setSessionType(HelpDeskAiSessionContext.SessionType.AGENT_SERVICE);

    ProxyConversation aiConversation = new ProxyConversation(true,
        session.getAgentMakerCheckerService());
    aiConversation.addProxyId(ticket.getClientStreamId());

    session.getHelpDeskAi().startConversation(aiSessionContext.getAiSessionKey(), aiConversation);

    proxyMap.put(ticket.getId(), new MessageProxy());
    proxyMap.get(ticket.getId()).addProxyConversation(aiConversation);
  }

  /**
   * If a proxy has already been created for the ticket, but the agent has not been mapped,
   * map the agent.
   * @param ticket the ticket to base the mapping on.
   * @param aiSessionContext the ai session context
   */
  private void addAgentToProxy(Ticket ticket, HelpDeskAiSessionContext aiSessionContext) {
    aiSessionContext.setGroupId(session.getGroupId());
    aiSessionContext.setSessionType(HelpDeskAiSessionContext.SessionType.AGENT_SERVICE);


    ProxyConversation aiConversation = new ProxyConversation(true,
        session.getClientMakerCheckerService());
    aiConversation.addProxyId(ticket.getServiceStreamId());
    session.getHelpDeskAi().startConversation(aiSessionContext.getAiSessionKey(), aiConversation);

    proxyMap.get(ticket.getId()).addProxyConversation(aiConversation);
  }

  /**
   * Creates a new proxy for the client. This includes:
   *    Creating a new session with the help desk ai, and adding a new ai conversation.
   *    Registering the proxy in the proxy map.
   * @param ticket
   */
  private void createClientProxy(Ticket ticket, HelpDeskAiSessionContext aiSessionContext) {
    aiSessionContext.setGroupId(session.getGroupId());
    aiSessionContext.setSessionType(HelpDeskAiSessionContext.SessionType.CLIENT);

    ProxyConversation aiConversation = new ProxyConversation(false, session.getClientMakerCheckerService());
    aiConversation.addProxyId(ticket.getServiceStreamId());
    session.getHelpDeskAi().startConversation(aiSessionContext.getAiSessionKey(), aiConversation);

    proxyMap.put(ticket.getId(), new MessageProxy());
    proxyMap.get(ticket.getId()).addProxyConversation(aiConversation);
  }

  private void sendClaimTicketMessage(String ticketId) {
    MessageTemplate messageTemplate = new MessageTemplate(session.getClaimMessageTemplate());
    MessageTemplate entityTemplate = new MessageTemplate(session.getClaimEntityTemplate());
    ClaimMessageTemplateData messageTemplateData = new ClaimMessageTemplateData(ticketId);
    ClaimEntityTemplateData entityTemplateData = new ClaimEntityTemplateData(ticketId);
    String message = messageTemplate.buildFromData(messageTemplateData);
    String entity = entityTemplate.buildFromData(entityTemplateData);

    SymMessage symphonyMessage = new SymMessage();
    symphonyMessage.setMessage(message);
    symphonyMessage.setEntityData(entity);
    symphonyMessage.setStreamId(session.getAgentStreamId());
    Stream stream = new Stream();
    stream.setId(session.getAgentStreamId());
    try {
      session.getSymphonyClient().getMessagesClient().sendMessage(stream, symphonyMessage);
    } catch (MessagesException e) {
      LOG.error("Failed to send claim message: ", e);
    }
  }

  /**
   * Creates a new service stream for a ticket.
   * @param ticketId the ticket ID to create the service stream for
   * @param streamId the clients stream ID
   * @return the stream ID for the new service stream
   */
  private String newServiceStream(String ticketId, String streamId) {
    SymRoomAttributes roomAttributes = new SymRoomAttributes();
    roomAttributes.setCreatorUser(session.getSymphonyClient().getLocalUser());

    String users = "";
    for(SymUser symUser: session.getSymphonyClient().getChatService().getChatByStream(streamId).getRemoteUsers()) {
      users += symUser.getFirstName() + " " + symUser.getLastName() + ", ";
    }
    users = users.substring(0, users.length() - 3);

    roomAttributes.setDescription("Service room for users " + users + ".");
    roomAttributes.setDiscoverable(false);
    roomAttributes.setMembersCanInvite(true);
    roomAttributes.setName(session.getGroupId() + " Ticket Room (" + ticketId + ")");
    roomAttributes.setReadOnly(false);
    roomAttributes.setPublic(false);

    Room room = null;
    try {
      room = session.getSymphonyClient().getRoomService().createRoom(roomAttributes);
    } catch (RoomException e) {
      LOG.error("Create room failed: ", e);
    }

    return room.getStreamId();
  }

}
