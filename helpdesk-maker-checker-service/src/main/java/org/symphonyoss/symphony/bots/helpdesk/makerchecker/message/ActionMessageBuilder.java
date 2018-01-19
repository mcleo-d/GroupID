package org.symphonyoss.symphony.bots.helpdesk.makerchecker.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.symphonyoss.symphony.bots.helpdesk.service.model.Makerchecker;
import org.symphonyoss.symphony.bots.helpdesk.service.model.UserInfo;
import org.symphonyoss.symphony.bots.utility.message.EntityBuilder;
import org.symphonyoss.symphony.bots.utility.message.SymMessageBuilder;
import org.symphonyoss.symphony.bots.utility.validation.SymphonyValidationUtil;
import org.symphonyoss.symphony.clients.model.SymMessage;
import org.symphonyoss.symphony.clients.model.SymUser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by crepache on 18/01/18.
 */
public class ActionMessageBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(MakerCheckerMessageBuilder.class);

  private static final String BASE_EVENT = "com.symphony.bots.helpdesk.event.makerchecker.action.performed";

  private static final String VERSION = "1.0";

  private static final String MAKER_CHECKER_ACTION_PERFORMED_MESSAGE_TEMPLATE = "ActionMessage.xml";

  private static String message;

  private final SymMessageBuilder messageBuilder;

  private String makerCheckerId;

  private String state;

  private Long userId;

  @Autowired
  private SymphonyValidationUtil symphonyValidationUtil;

  public ActionMessageBuilder() {
    if (StringUtils.isEmpty(message)) {
      message = parseTemplate();
    }

    this.messageBuilder = SymMessageBuilder.message(message);
  }

  public ActionMessageBuilder makerCheckerId(String makerCheckerId) {
    this.makerCheckerId = makerCheckerId;
    return this;
  }

  public ActionMessageBuilder state(String state) {
    this.state = state;
    return this;
  }

  public ActionMessageBuilder userId(Long userId) {
    this.userId = userId;
    return this;
  }

  private String parseTemplate() {
    StringBuilder message = new StringBuilder();
    InputStream resource = getClass().getClassLoader().getResourceAsStream(MAKER_CHECKER_ACTION_PERFORMED_MESSAGE_TEMPLATE);

    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(resource))) {
      buffer.lines().forEach(message::append);
    } catch (IOException e) {
      LOGGER.error("Fail to parse maker checker message template");
    }

    return message.toString();
  }

  private UserInfo getUser() {
    UserInfo user = new UserInfo();

    SymUser symUser = symphonyValidationUtil.validateUserId(userId);
    user.setDisplayName(symUser.getDisplayName());
    user.setUserId(symUser.getId());

    return user;
  }

  public SymMessage build() {
    if (messageBuilder == null) {
      return null;
    }

    try {
      EntityBuilder bodyBuilder = EntityBuilder.createEntity(BASE_EVENT, VERSION);

      UserInfo user = getUser();
      bodyBuilder.addField("user", user);
      bodyBuilder.addField("makerCheckerId", makerCheckerId);
      bodyBuilder.addField("state", state);

      EntityBuilder builder = EntityBuilder.createEntity();
      builder.addField("makerchecker", bodyBuilder.toObject());

      String entityData = builder.build();

      return messageBuilder.entityData(entityData).build();
    } catch (JsonProcessingException e) {
      LOGGER.error("Fail to create entity data");
      return null;
    }
  }

}
