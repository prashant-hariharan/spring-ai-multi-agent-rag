package com.prashant.springai.rag.config;

import com.prashant.springai.rag.utils.AIProviderConstants;
import com.prashant.springai.rag.utils.PromptReaderUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties(AIProviderProperties.class)
public class MultiModelConfig {

  private final ResourceLoader resourceLoader;

  @Value("${app.ai.llm-logging.enabled:false}")
  private boolean llmLoggingEnabled;


  @Value("${app.ai.chat-memory.enabled:false}")
  private boolean chatMemoryEnabled;

  @Value("${app.ai.chat-memory.max-messages:10}")
  private int chatMemoryMaxMessages;

  @Value("${app.ai.guardrails.enabled:true}")
  private boolean appGuardrailsEnabled;

  public MultiModelConfig(
    ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }


  @Bean(AIProviderConstants.OPENAI)
  @Primary
  @ConditionalOnBean(OpenAiChatModel.class)
  @ConditionalOnProperty(prefix = "spring.ai.openai.chat.options", name = "model")
  public ChatClient openAIChatClient(OpenAiChatModel openAiChatModel,
    @Value("${spring.ai.openai.chat.options.max-tokens:0}") Integer openAiMaxTokens,ChatMemory chatMemory) {
    String systemPrompt = loadSystemPrompt("classpath:prompts/system-prompts/openai-system.txt", openAiMaxTokens);
    return applyAdvisors(ChatClient.builder(openAiChatModel),chatMemory)
      .defaultSystem(systemPrompt)
      .build();
  }

  @Bean(AIProviderConstants.GEMINI)
  @ConditionalOnProperty(prefix = "spring.ai.providers.gemini", name = "model")
  public ChatClient geminiChatClient(AIProviderProperties properties, ChatMemory chatMemory) {
    AIProviderProperties.Provider provider = requireProvider(properties, AIProviderConstants.GEMINI);
    Integer maxTokens = provider.getMaxTokens();
    String systemPrompt = loadSystemPrompt("classpath:prompts/system-prompts/gemini-system.txt", maxTokens);
    return applyAdvisors(
      ChatClient.builder(createOpenAiCompatibleModel(properties, AIProviderConstants.GEMINI))
      ,chatMemory)
      .defaultSystem(systemPrompt)
      .build();
  }

  @Bean(AIProviderConstants.OLLAMA)
  @ConditionalOnProperty(prefix = "spring.ai.providers.ollama", name = "model")
  public ChatClient ollamaChatClient(AIProviderProperties properties, ChatMemory chatMemory) {
    AIProviderProperties.Provider provider = requireProvider(properties, AIProviderConstants.OLLAMA);
    Integer maxTokens = provider.getMaxTokens();
    String systemPrompt = loadSystemPrompt("classpath:prompts/system-prompts/ollama-system.txt", maxTokens);
    return applyAdvisors(
      ChatClient.builder(createOpenAiCompatibleModel(properties, AIProviderConstants.OLLAMA))
      ,chatMemory)
      .defaultSystem(systemPrompt)
      .build();
  }

  @Bean(AIProviderConstants.GROQ)
  @ConditionalOnProperty(prefix = "spring.ai.providers.groq", name = "model")
  public ChatClient groqChatClient(AIProviderProperties properties,ChatMemory chatMemory) {
    AIProviderProperties.Provider provider = requireProvider(properties, AIProviderConstants.GROQ);
    Integer maxTokens = provider.getMaxTokens();
    String systemPrompt = loadSystemPrompt("classpath:prompts/system-prompts/groq-system.txt", maxTokens);
    return applyAdvisors(
      ChatClient.builder(createOpenAiCompatibleModel(properties, AIProviderConstants.GROQ))
      ,chatMemory)
      .defaultSystem(systemPrompt)
      .build();
  }


  private OpenAiChatModel createOpenAiCompatibleModel(
    AIProviderProperties properties,
    String providerName) {
    //get provider properties based on model name
    AIProviderProperties.Provider provider = requireProvider(properties, providerName);

    OpenAiApi openAiApi = OpenAiApi.builder()
      .apiKey(provider.getApiKey())
      .baseUrl(provider.getBaseUrl())
      .completionsPath(provider.getCompletionPath())
      .build();

    OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(provider.getModel());

    //optional params for temperature and max tokens
    Optional.ofNullable(provider.getTemperature())
      .ifPresent(optionsBuilder::temperature);

    Optional.ofNullable(provider.getMaxTokens())
      .ifPresent(optionsBuilder::maxTokens);

    return OpenAiChatModel.builder()
      .openAiApi(openAiApi)
      .defaultOptions(optionsBuilder.build())
      .build();
  }


  private AIProviderProperties.Provider requireProvider(AIProviderProperties properties, String providerName) {
    if (properties.getProviders() == null || !properties.getProviders().containsKey(providerName)) {
      throw new IllegalStateException("Missing spring.ai.providers." + providerName + " configuration");
    }
    return properties.getProviders().get(providerName);
  }



  private String loadSystemPrompt(String location, Integer maxTokens) {
    String prompt = PromptReaderUtil.getPrompt(resourceLoader,location);
    if (maxTokens != null && maxTokens > 0) {
      prompt = prompt.replace("${MAX_TOKENS}", maxTokens.toString());
    }
    return prompt;
  }


  private ChatClient.Builder applyAdvisors(ChatClient.Builder builder, ChatMemory chatMemory) {
    List<Advisor> advisors = new ArrayList<>();
    if (chatMemoryEnabled) {
      advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
    }
    if (llmLoggingEnabled) {
      advisors.add(new SimpleLoggerAdvisor());
    }
    if (appGuardrailsEnabled) {
      advisors.add(
        SafeGuardAdvisor.builder()
          .sensitiveWords(getSensitiveWords())
          .failureResponse("Request blocked by guardrail.")
          .build()
      );
    }
    if (!advisors.isEmpty()) {
      return builder.defaultAdvisors(advisors);
    }

    return builder;
  }

  private List<String> getSensitiveWords() {
    return List.of(
      "ignore previous instructions",
      "disregard previous instructions",
      "reveal system prompt",
      "show system prompt",
      "developer message",
      "hidden instructions",
      "bypass safety",
      "override policy",
      "jailbreak"
    );
  }
}
