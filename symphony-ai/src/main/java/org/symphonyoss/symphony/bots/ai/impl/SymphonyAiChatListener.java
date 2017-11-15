package org.symphonyoss.symphony.bots.ai.impl;

import org.symphonyoss.symphony.bots.ai.model.AiSessionKey;

import org.symphonyoss.client.services.ChatListener;

/**
 * Created by nick.tarsillo on 9/27/17.
 * Symphony chat listener for Symphony Ai.
 */
public abstract class SymphonyAiChatListener implements ChatListener {
  private AiSessionKey aiSessionKey;

  public AiSessionKey getAiSessionKey() {
    return aiSessionKey;
  }

  public void setAiSessionKey(AiSessionKey aiSessionKey) {
    this.aiSessionKey = aiSessionKey;
  }
}