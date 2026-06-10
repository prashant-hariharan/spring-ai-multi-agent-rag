# Agent Query Flow

```mermaid
flowchart TD
    A[Client POST /agent/query<br/>body: query text] --> B[AgentController]
    B --> C[AgentOrchestratorService.process]

    C --> D[Extract question and order number]
    D --> E[LLM classify route and intent<br/>returns route + intent]

    E -->|success| F[route + intent selected]
    E -->|fail/incomplete| E2[Heuristic fallback<br/>AgentRouteHeuristicResolver + AgentIntentHeuristicResolver]
    E2 --> F

    F --> G{Select handler by route}

    G -->|RAG| H[RagRouteHandler]
    G -->|TOOLS| I[ToolsRouteHandler]
    G -->|COMBINED| J[CombinedRouteHandler]
    G -->|GENERAL| K[GeneralRouteHandler]

    H --> H1[Resolve doc scope from intent<br/>RagDocumentScopeResolver]
    H1 --> H2[RAGQueryService.askQuestion]
    H2 --> L[RouteExecutionResult<br/>response + evidence]

    I --> I1{orderNumber present?}
    I1 -->|no| I2[Error response]
    I1 -->|yes| I3[LLMOrderTrackingService.getOrderInformationFromLLM]
    I3 --> L
    I2 --> Z[AgentQueryResponse]

    J --> J1{orderNumber present?}
    J1 -->|no| J2[Error response]
    J1 -->|yes| J3[OrderService get order by number<br/>deterministic transactional facts]
    J3 --> J4[RAGQueryService fetch relevant context<br/>doc scope from intent]
    J4 --> J5[Final synthesis LLM call<br/>combined route synthesis prompt]
    J5 --> L
    J2 --> Z

    K --> K1[Chat with OLLAMA<br/>non-critical/general]
    K1 --> L

    L --> M{Route response successful?}
    M -->|no| Z
    M -->|yes| N[AnswerEvaluationService.evaluate<br/>route + question + evidence + answer]
    N --> O{Evaluation passed?}
    O -->|yes| Z
    O -->|no and attempts remain| P[Use evaluation feedback<br/>as retry instruction]
    P --> G
    O -->|no attempts left| Q[Return validation failure<br/>with last answer]
    Q --> Z
```
