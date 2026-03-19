package com.aichatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class AiService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getResponse(String aiType, String prompt, List<Map<String, String>> conversationHistory,
                             String model, String geminiApiKey, String openaiApiKey, double temperature) {
        try {
            System.out.println("AI 타입: " + (aiType != null ? aiType : "null"));

            // AI 타입에 따라 분기 처리
            if (aiType != null) {
                if (aiType.contains("Gemini") || aiType.contains("제미나이") || aiType.contains("gemini")) {
                    System.out.println("Gemini API 사용");
                    return getGeminiResponse(prompt, conversationHistory, model, geminiApiKey, temperature);
                } else if (aiType.contains("OpenAI") || aiType.contains("ChatGPT") || aiType.contains("지피티") || aiType.contains("gpt")) {
                    System.out.println("OpenAI API 사용");
                    return getOpenaiResponse(prompt, conversationHistory, model, openaiApiKey, temperature);
                }
            }

            // 기본값: Gemini 사용
            System.out.println("기본값: Gemini API 사용");
            return getGeminiResponse(prompt, conversationHistory, model != null ? model : "gemini-pro", geminiApiKey, temperature);
        } catch (Exception e) {
            System.err.println("❌ getResponse에서 예외 발생: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            // 예외 발생 시 기본 응답 반환
            return generateChatGPTStyleResponse(prompt, conversationHistory != null ? conversationHistory : new ArrayList<>());
        }
    }


    private String generateChatGPTStyleResponse(String prompt, List<Map<String, String>> conversationHistory) {
        // ChatGPT 스타일 즉시 응답 생성 (로딩 시간 없음)
        String lowerPrompt = prompt.toLowerCase().trim();

        // 인사말 처리
        if (lowerPrompt.matches(".*(안녕|hello|hi|헬로|하이).*")) {
            if (conversationHistory == null || conversationHistory.isEmpty()) {
                return "안녕하세요! 저는 AI 어시스턴트입니다. 무엇을 도와드릴까요?";
            } else {
                return "안녕하세요! 또 무엇을 도와드릴까요?";
            }
        }

        // 질문 처리
        if (lowerPrompt.matches(".*(뭐|무엇|what|어떻게|how|왜|why|언제|when|어디|where).*")) {
            if (lowerPrompt.contains("시간") || lowerPrompt.contains("time")) {
                return "현재 시간은 " + new java.util.Date() + " 입니다.";
            }
            if (lowerPrompt.contains("날씨") || lowerPrompt.contains("weather")) {
                return "죄송하지만 실시간 날씨 정보는 제공할 수 없습니다. 날씨 앱이나 웹사이트를 확인해주세요.";
            }
            if (lowerPrompt.contains("이름") || lowerPrompt.contains("name")) {
                return "저는 AI 챗봇입니다. 이름을 정해주시면 그렇게 불러드리겠습니다!";
            }
            return "좋은 질문이네요! 구체적으로 설명해주시면 더 정확한 답변을 드릴 수 있습니다.";
        }

        // 감사 표현
        if (lowerPrompt.matches(".*(고마워|감사|thanks|thank you|thank).*")) {
            return "천만에요! 다른 도움이 필요하시면 언제든지 말씀해주세요.";
        }

        // 작별 인사
        if (lowerPrompt.matches(".*(안녕히|bye|goodbye|잘가|나중에).*")) {
            return "안녕히 가세요! 또 만나요!";
        }

        // 대화 맥락 기반 응답
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            // 이전 대화를 고려한 응답
            String lastUserMessage = "";
            for (int i = conversationHistory.size() - 1; i >= 0; i--) {
                Map<String, String> msg = conversationHistory.get(i);
                if ("user".equals(msg.get("role"))) {
                    lastUserMessage = msg.get("content").toLowerCase();
                    break;
                }
            }

            // 연속된 질문에 대한 응답
            if (lastUserMessage.contains("뭐") || lastUserMessage.contains("무엇")) {
                return "앞서 질문하신 내용과 관련하여, 더 구체적인 정보를 알려주시면 도움을 드릴 수 있습니다.";
            }
        }

        // 숫자만 입력된 경우
        if (lowerPrompt.matches("^\\d+$")) {
            try {
                int number = Integer.parseInt(lowerPrompt);
                if (number == 1) {
                    return "네, 말씀하세요. 무엇을 도와드릴까요?";
                } else if (number < 10) {
                    return "네, " + number + "번째 질문이시군요. 무엇을 도와드릴까요?";
                } else if (number < 100) {
                    return "네, 알겠습니다. 무엇을 도와드릴까요?";
                } else if (number < 1000) {
                    return "네, 이해했습니다. 구체적으로 질문해주시면 답변해드리겠습니다.";
                } else {
                    return "네, 알겠습니다. 무엇을 도와드릴까요?";
                }
            } catch (NumberFormatException e) {
                // 숫자 파싱 실패 시 일반 응답으로
            }
        }

        // 일반적인 대화형 응답
        if (lowerPrompt.length() < 10) {
            return "네, 말씀하세요. 무엇을 도와드릴까요?";
        }

        // 계산 관련
        if (lowerPrompt.matches(".*[0-9]+.*[+\\-*/].*[0-9]+.*")) {
            return "죄송하지만 계산 기능은 현재 사용할 수 없습니다. 계산기 앱을 사용해주세요.";
        }

        // ChatGPT 스타일의 자연스러운 응답
        String[] responses = {
            "이해했습니다. '" + prompt + "'에 대해 말씀하시는군요. 더 구체적으로 설명해주시면 도움을 드릴 수 있습니다.",
            "흥미로운 주제네요! '" + prompt + "'에 대해 더 자세히 알려주시면 답변해드리겠습니다.",
            "알겠습니다. '" + prompt + "'에 관해 질문이 있으시면 구체적으로 말씀해주세요.",
            "네, 이해했습니다. '" + prompt + "'에 대해 더 자세한 정보를 주시면 도움을 드릴 수 있습니다."
        };

        // 프롬프트 길이와 내용에 따라 응답 선택
        int responseIndex = Math.abs(prompt.hashCode()) % responses.length;
        return responses[responseIndex];
    }


    private String getGeminiResponse(String prompt, List<Map<String, String>> conversationHistory,
                                    String model, String apiKey, double temperature) {
        try {
            if (!checkInternet()) {
                return "❌ 인터넷 연결을 확인할 수 없습니다.";
            }

            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "⚠️ Gemini API를 사용하려면 API 키가 필요합니다.\n\n" +
                       "API 키 발급: https://aistudio.google.com/app/apikey\n\n" +
                       "또는 '무료 AI 챗봇 (API 키 불필요)' 옵션을 사용하세요.";
            }

            apiKey = apiKey.trim();

            // API 키 유효성 검사 강화
            if (apiKey.length() < 20) {
                return "❌ API 키가 너무 짧습니다. 올바른 API 키를 입력했는지 확인하세요.\n\n" +
                       "Gemini API 키는 보통 39자입니다.\n" +
                       "API 키 발급: https://aistudio.google.com/app/apikey";
            }

            // API 키에 특수 문자가 포함되어 있는지 확인 (에러 메시지가 키로 들어간 경우 방지)
            if (apiKey.contains("❌") || apiKey.contains("오류") || apiKey.contains("해결 방법") ||
                apiKey.contains("API 할당량") || apiKey.contains("상태 코드") ||
                apiKey.contains("::") || apiKey.contains("Spring") || apiKey.contains("INFO") ||
                apiKey.contains("ERROR") || apiKey.contains("WARN") || apiKey.contains("DEBUG") ||
                apiKey.contains("로그") || apiKey.contains("log") || apiKey.contains("Log")) {
                return "❌ API 키가 올바르지 않습니다. API 키 입력란에 오류 메시지나 로그가 포함되어 있습니다.\n\n" +
                       "💡 해결 방법:\n" +
                       "1. API 키 입력란을 완전히 비우세요\n" +
                       "2. [Google AI Studio](https://aistudio.google.com/app/apikey)에서 새 API 키를 발급받으세요\n" +
                       "3. API 키만 복사해서 붙여넣으세요 (다른 텍스트는 포함하지 마세요)\n\n" +
                       "✅ 올바른 API 키 형식:\n" +
                       "   • 'AIza'로 시작하는 긴 문자열\n" +
                       "   • 보통 39자 정도\n" +
                       "   • 예: AIzaSy... (나머지 35자)";
            }

            // API 키 형식 확인 (Gemini API 키는 보통 'AIza'로 시작)
            if (!apiKey.startsWith("AIza")) {
                return "❌ API 키 형식이 올바르지 않습니다.\n\n" +
                       "Gemini API 키는 'AIza'로 시작해야 합니다.\n" +
                       "현재 입력된 키 앞부분: " + apiKey.substring(0, Math.min(20, apiKey.length())) + "...\n\n" +
                       "💡 해결 방법:\n" +
                       "1. API 키 입력란을 완전히 비우세요\n" +
                       "2. [Google AI Studio](https://aistudio.google.com/app/apikey)에서 새 API 키를 발급받으세요\n" +
                       "3. API 키만 복사해서 붙여넣으세요\n\n" +
                       "✅ 올바른 API 키는 'AIza'로 시작하는 39자 정도의 문자열입니다.";
            }

            // 모델 이름 유효성 검사
            if (model == null || model.trim().isEmpty()) {
                model = "gemini-1.5-flash";
            }
            model = model.trim();

            // API 키 URL 인코딩
            String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

            // ListModels API를 호출하여 사용 가능한 모델 목록 가져오기
            List<Map<String, String>> availableModels = null;
            try {
                availableModels = getAvailableModels(encodedApiKey);
            } catch (Exception e) {
                // ListModels 실패 시 무시하고 기본 모델 사용
                System.err.println("ListModels API 호출 실패: " + e.getMessage());
            }

            // 사용 가능한 모델 목록 (우선순위 순서)
            List<Map<String, String>> modelsToTry = new ArrayList<>();

            if (availableModels != null && !availableModels.isEmpty()) {
                // ListModels에서 가져온 사용 가능한 모델 사용
                // 사용자가 선택한 모델을 먼저 찾기
                boolean foundSelected = false;
                for (Map<String, String> availableModel : availableModels) {
                    String availableModelName = availableModel.get("model");

                    if (availableModelName.equals(model) ||
                        (model.contains("1.5") && availableModelName.contains("1.5")) ||
                        (model.equals("gemini-pro") && availableModelName.contains("gemini-pro"))) {
                        modelsToTry.add(0, availableModel); // 선택한 모델을 맨 앞에
                        foundSelected = true;
                        break;
                    }
                }

                // 선택한 모델이 없으면 모든 사용 가능한 모델 추가
                if (!foundSelected) {
                    modelsToTry.addAll(availableModels);
                } else {
                    // 나머지 사용 가능한 모델들도 추가
                    for (Map<String, String> availableModel : availableModels) {
                        if (!modelsToTry.contains(availableModel)) {
                            modelsToTry.add(availableModel);
                        }
                    }
                }
            } else {
                // ListModels 실패 시 기본 모델 목록 사용 (최신 모델 우선)
                // 최신 Gemini 모델들 시도
                modelsToTry.add(createModelEntry("gemini-1.5-flash-latest", "v1beta"));
                modelsToTry.add(createModelEntry("gemini-1.5-pro-latest", "v1beta"));
                modelsToTry.add(createModelEntry("gemini-1.5-flash", "v1beta"));
                modelsToTry.add(createModelEntry("gemini-1.5-pro", "v1beta"));

                // 사용자가 선택한 모델도 추가
                if (model.contains("1.5")) {
                    modelsToTry.add(0, createModelEntry(model, "v1beta"));
                } else if (model.equals("gemini-pro")) {
                    modelsToTry.add(createModelEntry("gemini-pro", "v1"));
                    modelsToTry.add(createModelEntry("gemini-pro", "v1beta"));
                } else {
                    modelsToTry.add(createModelEntry(model, "v1beta"));
                }
            }

            // 첫 번째 모델로 시도
            Map<String, String> firstModelEntry = modelsToTry.get(0);
            String apiVersion = firstModelEntry.get("version");
            String modelName = firstModelEntry.get("model");

            String apiUrl = "https://generativelanguage.googleapis.com/" + apiVersion + "/models/" +
                           modelName + ":generateContent?key=" + encodedApiKey;

            // 대화 기록 구성 - 현재 프롬프트만 사용 (이전 대화 기록 제외)
            List<Map<String, Object>> contents = new ArrayList<>();
            // 현재 프롬프트만 사용하여 이전 대화에 대한 답변이 포함되지 않도록 함
            Map<String, Object> userPart = new HashMap<>();
            userPart.put("text", prompt);
            Map<String, Object> userContent = new HashMap<>();
            userContent.put("role", "user");
            userContent.put("parts", Arrays.asList(userPart));
            contents.add(userContent);

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", temperature);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 2048);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", contents);
            requestBody.put("generationConfig", generationConfig);

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(response.body());
                if (result.has("candidates") && result.get("candidates").size() > 0) {
                    JsonNode candidate = result.get("candidates").get(0);
                    if (candidate.has("content") && candidate.get("content").has("parts")) {
                        return candidate.get("content").get("parts").get(0).get("text").asText();
                    }
                }
                return "응답을 생성할 수 없습니다.";
            } else if (response.statusCode() == 404) {
                // 에러 응답 확인
                String errorDetail = "";
                try {
                    JsonNode errorData = objectMapper.readTree(response.body());
                    if (errorData.has("error")) {
                        if (errorData.get("error").has("message")) {
                            errorDetail = errorData.get("error").get("message").asText();
                        }
                        if (errorData.get("error").has("status")) {
                            errorDetail += " (상태: " + errorData.get("error").get("status").asText() + ")";
                        }
                    }
                } catch (Exception e) {
                    errorDetail = response.body();
                }

                // 다른 모델로 순차적으로 재시도
                for (Map<String, String> modelEntry : modelsToTry) {
                    String fallbackModel = modelEntry.get("model");
                    String fallbackVersion = modelEntry.get("version");

                    // 이미 시도한 모델은 건너뛰기
                    if (fallbackModel.equals(modelName) && fallbackVersion.equals(apiVersion)) {
                        continue;
                    }

                    try {
                        String fallbackUrl = "https://generativelanguage.googleapis.com/" + fallbackVersion + "/models/" +
                                           fallbackModel + ":generateContent?key=" + encodedApiKey;
                            HttpRequest fallbackRequest = HttpRequest.newBuilder()
                                .uri(URI.create(fallbackUrl))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                                .timeout(Duration.ofSeconds(30))
                                .build();

                            HttpResponse<String> fallbackResponse = client.send(fallbackRequest, HttpResponse.BodyHandlers.ofString());
                            if (fallbackResponse.statusCode() == 200) {
                                JsonNode result = objectMapper.readTree(fallbackResponse.body());
                                if (result.has("candidates") && result.get("candidates").size() > 0) {
                                    JsonNode candidate = result.get("candidates").get(0);
                                    if (candidate.has("content") && candidate.get("content").has("parts")) {
                                        return candidate.get("content").get("parts").get(0).get("text").asText();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }

                // 모든 모델 시도 실패 - API 키 문제일 가능성
                return "❌ 모든 모델 시도 실패 (상태 코드: 404)\n" +
                       (errorDetail.isEmpty() ? "" : "오류 상세: " + errorDetail + "\n\n") +
                       "가능한 원인:\n" +
                       "1. API 키가 유효하지 않거나 만료되었습니다\n" +
                       "2. API 키에 Gemini API 접근 권한이 없습니다\n" +
                       "3. API 키가 해당 모델들에 대한 접근 권한이 없습니다\n" +
                       "4. API 키 형식이 올바르지 않습니다\n\n" +
                       "💡 해결 방법:\n" +
                       "1. API 키 확인:\n" +
                       "   • API 키는 'AIza'로 시작해야 합니다\n" +
                       "   • API 키 길이는 보통 39자입니다\n" +
                       "   • 입력한 키 앞부분: " + apiKey.substring(0, Math.min(15, apiKey.length())) + "...\n\n" +
                       "2. https://aistudio.google.com/app/apikey 에서 새 API 키를 발급받으세요\n" +
                       "3. API 키가 Gemini API에 대한 접근 권한이 있는지 확인하세요\n" +
                       "4. Google AI Studio에서 API 키 설정을 확인하세요\n" +
                       "5. 또는 '무료 AI 챗봇 (API 키 불필요)' 옵션을 사용하세요 (API 키 불필요)";
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                // 인증 오류
                JsonNode errorData = objectMapper.readTree(response.body());
                String errorMsg = errorData.has("error") && errorData.get("error").has("message") ?
                    errorData.get("error").get("message").asText() : "API 키가 유효하지 않습니다";
                return "❌ API 키 인증 실패 (상태 코드: " + response.statusCode() + ")\n" +
                       errorMsg + "\n\n" +
                       "해결 방법:\n" +
                       "1. https://aistudio.google.com/app/apikey 에서 새 API 키를 발급받으세요\n" +
                       "2. API 키를 올바르게 입력했는지 확인하세요\n" +
                       "3. API 키에 Gemini API 접근 권한이 있는지 확인하세요";
            } else if (response.statusCode() == 429) {
                // 할당량 초과 오류
                JsonNode errorData = objectMapper.readTree(response.body());
                String errorMsg = errorData.has("error") && errorData.get("error").has("message") ?
                    errorData.get("error").get("message").asText() : "할당량이 초과되었습니다";

                // 재시도 시간 추출 (에러 메시지에서 "Please retry in Xs" 패턴 찾기)
                String retryAfter = "";
                int retrySeconds = 0;
                if (errorMsg.contains("Please retry in")) {
                    try {
                        String[] parts = errorMsg.split("Please retry in ");
                        if (parts.length > 1) {
                            String timePart = parts[1].split("\\.")[0]; // 소수점 제거
                            String secondsStr = timePart.replaceAll("[^0-9]", "");
                            if (!secondsStr.isEmpty()) {
                                retrySeconds = Integer.parseInt(secondsStr);
                                retryAfter = retrySeconds + "초";
                            }
                        }
                    } catch (Exception e) {
                        // 추출 실패 시 무시
                    }
                }

                // limit: 0 확인 (무료 할당량 없음)
                boolean noFreeQuota = errorMsg.contains("limit: 0");

                // 재시도 시간이 있으면 일시적 할당량 초과로 판단
                boolean isTemporaryQuotaExceeded = retrySeconds > 0;

                String quotaMessage;
                if (noFreeQuota && !isTemporaryQuotaExceeded) {
                    // 재시도 시간이 없고 limit: 0이면 진짜 할당량 없음
                    quotaMessage = "⚠️ 무료 할당량이 0으로 설정되어 있습니다.\n\n" +
                                  "가능한 원인:\n" +
                                  "• API 키가 무료 등급이 아닐 수 있습니다\n" +
                                  "• Google AI Studio에서 무료 할당량이 비활성화되었을 수 있습니다\n" +
                                  "• API 키 생성 시 무료 할당량이 포함되지 않았을 수 있습니다\n" +
                                  "• Google의 정책 변경으로 인해 일부 API 키에 무료 할당량이 없을 수 있습니다\n\n";
                } else if (isTemporaryQuotaExceeded) {
                    // 재시도 시간이 있으면 일시적 초과
                    quotaMessage = "⚠️ 일시적으로 할당량이 초과되었습니다.\n" +
                                  "재시도 시간이 제공되었으므로 할당량이 있는 것으로 보입니다.\n\n";
                } else {
                    quotaMessage = "⚠️ 무료 할당량이 모두 소진되었습니다.\n\n";
                }

                String solutionMessage;
                if (isTemporaryQuotaExceeded) {
                    // 재시도 시간이 있지만, 실제로는 할당량이 없을 수 있음
                    solutionMessage = "💡 해결 방법:\n" +
                                     "1. ⏰ " + retryAfter + " 후 다시 시도해보세요\n" +
                                     "   (재시도 시간이 제공되었으므로 할당량이 있을 수 있습니다)\n\n" +
                                     "2. 🔑 새 API 키 발급:\n" +
                                     "   • https://aistudio.google.com/app/apikey 에서 새 API 키 발급\n" +
                                     "   • 새 API 키는 무료 할당량이 포함될 수 있습니다\n\n" +
                                     "3. 🔍 할당량 확인:\n" +
                                     "   • https://ai.dev/usage?tab=rate-limit 에서 할당량 상태 확인\n" +
                                     "   • 현재 API 키의 사용량 및 제한 확인\n\n" +
                                     "4. ⚠️ 재시도해도 같은 오류가 발생한다면:\n" +
                                     "   • 할당량이 없거나 제한이 매우 엄격할 수 있습니다\n" +
                                     "   • 새 API 키를 발급받아 시도해보세요\n\n" +
                                     "📝 참고: 재시도 시간이 제공되었지만, 연속으로 같은 오류가 발생하면\n" +
                                     "실제로 할당량이 없거나 제한이 매우 엄격할 수 있습니다.";
                } else {
                    solutionMessage = "💡 해결 방법:\n" +
                                     "1. 🔑 새 API 키 발급 (가장 확실한 방법):\n" +
                                     "   • https://aistudio.google.com/app/apikey 에서 새 API 키 발급\n" +
                                     "   • 새 API 키는 무료 할당량이 포함될 수 있습니다\n" +
                                     "   • 기존 API 키를 삭제하고 새로 생성해보세요\n\n" +
                                     "2. ⏰ " + (retryAfter.isEmpty() ? "잠시 후 다시 시도" : retryAfter + " 후 다시 시도") + "\n" +
                                     "   (일시적인 제한일 수 있습니다)\n\n" +
                                     "3. 🔍 할당량 확인:\n" +
                                     "   • https://ai.dev/usage?tab=rate-limit 에서 사용량 확인\n" +
                                     "   • 현재 API 키의 할당량 상태 확인\n\n" +
                                     "4. 💡 다른 API 키 시도:\n" +
                                     "   • 다른 Google 계정으로 새 API 키 발급\n" +
                                     "   • 또는 다른 AI 서비스 사용 고려";
                }

                // 재시도 시간이 60초 이하면 자동 재시도
                if (isTemporaryQuotaExceeded && retrySeconds > 0 && retrySeconds <= 60) {
                    try {
                        System.out.println("[재시도] 할당량 초과로 인한 재시도 대기 중... (" + retrySeconds + "초)");
                        Thread.sleep((retrySeconds + 1) * 1000L); // 재시도 시간 + 1초 여유
                        System.out.println("[재시도] 재시도 시작...");
                        
                        // 재시도 (한 번만)
                        HttpResponse<String> retryResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (retryResponse.statusCode() == 200) {
                            JsonNode result = objectMapper.readTree(retryResponse.body());
                            if (result.has("candidates") && result.get("candidates").size() > 0) {
                                JsonNode candidate = result.get("candidates").get(0);
                                if (candidate.has("content") && candidate.get("content").has("parts")) {
                                    System.out.println("[재시도] 성공!");
                                    return candidate.get("content").get("parts").get(0).get("text").asText();
                                }
                            }
                        } else if (retryResponse.statusCode() == 429) {
                            // 재시도해도 여전히 429 에러
                            System.out.println("[재시도] 재시도 후에도 할당량 초과 지속");
                            return "❌ API 할당량 초과 (상태 코드: 429)\n\n" +
                                   "재시도 후에도 할당량 초과가 지속됩니다.\n\n" +
                                   quotaMessage +
                                   (retryAfter.isEmpty() ? "" : "⏰ 재시도 시간: 약 " + retryAfter + " 후\n\n") +
                                   solutionMessage;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println("[재시도] 재시도 중 오류: " + e.getMessage());
                    }
                }
                
                return "❌ API 할당량 초과 (상태 코드: 429)\n\n" +
                       quotaMessage +
                       (retryAfter.isEmpty() ? "" : "⏰ 재시도 시간: 약 " + retryAfter + " 후\n\n") +
                       solutionMessage;
            } else {
                JsonNode errorData = objectMapper.readTree(response.body());
                String errorMsg = errorData.has("error") && errorData.get("error").has("message") ?
                    errorData.get("error").get("message").asText() : "알 수 없는 오류";
                return "❌ Gemini API 호출 실패 (상태 코드: " + response.statusCode() + ")\n" + errorMsg;
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName();
            }

            // 더 자세한 오류 정보 제공
            String detailedError = "❌ Gemini API 호출 중 오류 발생\n\n";
            detailedError += "오류 내용: " + errorMsg + "\n\n";
            detailedError += "💡 해결 방법:\n";
            detailedError += "1. API 키 확인: " + (apiKey != null && apiKey.length() > 20 ?
                "API 키 길이는 정상입니다 (" + apiKey.length() + "자)" : "API 키가 너무 짧습니다") + "\n";
            detailedError += "2. 인터넷 연결 확인\n";
            detailedError += "3. https://aistudio.google.com/app/apikey 에서 API 키 유효성 확인\n";
            detailedError += "4. 잠시 후 다시 시도\n";
            detailedError += "5. 다른 모델 선택 시도 (gemini-1.5-flash, gemini-pro 등)";

            return detailedError;
        }
    }

    private String getOpenaiResponse(String prompt, List<Map<String, String>> conversationHistory,
                                    String model, String apiKey, double temperature) {
        try {
            if (!checkInternet()) {
                return "❌ 인터넷 연결을 확인할 수 없습니다.";
            }

            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "⚠️ ChatGPT를 사용하려면 API 키가 필요합니다.\n\n" +
                       "API 키 발급: https://platform.openai.com/api-keys\n\n" +
                       "신규 가입 시 무료 크레딧이 제공됩니다.\n\n" +
                       "또는 '간단한 챗봇 (로컬)' 옵션을 사용하세요.";
            }

            String apiUrl = "https://api.openai.com/v1/chat/completions";
            if (model == null || model.trim().isEmpty()) {
                model = "gpt-4o-mini";
            }

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "당신은 친절하고 도움이 되는 AI 어시스턴트입니다. 한국어로 답변해주세요.");
            messages.add(systemMsg);

            // 현재 프롬프트만 사용 (이전 대화 기록 제외)
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.trim());
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", 1000);

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(response.body());
                if (result.has("choices") && result.get("choices").size() > 0) {
                    return result.get("choices").get(0).get("message").get("content").asText();
                }
                return "응답을 생성할 수 없습니다.";
            } else if (response.statusCode() == 401) {
                return "❌ API 키가 유효하지 않습니다. 올바른 API 키를 입력해주세요.";
            } else if (response.statusCode() == 429) {
                return "❌ API 사용량 한도를 초과했습니다. 잠시 후 다시 시도해주세요.";
            } else {
                JsonNode errorData = objectMapper.readTree(response.body());
                String errorMsg = errorData.has("error") && errorData.get("error").has("message") ?
                    errorData.get("error").get("message").asText() : "알 수 없는 오류";
                return "❌ ChatGPT API 호출 실패 (상태 코드: " + response.statusCode() + ")\n" + errorMsg;
            }
        } catch (Exception e) {
            return "❌ 오류: " + e.getMessage();
        }
    }

    private Map<String, String> createModelEntry(String model, String version) {
        Map<String, String> entry = new HashMap<>();
        entry.put("model", model);
        entry.put("version", version);
        return entry;
    }

    private List<Map<String, String>> getAvailableModels(String encodedApiKey) {
        List<Map<String, String>> availableModels = new ArrayList<>();

        // v1beta와 v1 모두에서 ListModels 시도
        String[] versions = {"v1beta", "v1"};

        for (String version : versions) {
            try {
                String listUrl = "https://generativelanguage.googleapis.com/" + version + "/models?key=" + encodedApiKey;

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode result = objectMapper.readTree(response.body());
                    if (result.has("models")) {
                        for (JsonNode modelNode : result.get("models")) {
                            if (modelNode.has("name")) {
                                String modelName = modelNode.get("name").asText();
                                // "models/gemini-1.5-flash" 형식에서 "gemini-1.5-flash" 추출
                                if (modelName.contains("/")) {
                                    modelName = modelName.substring(modelName.lastIndexOf("/") + 1);
                                }

                                // generateContent 메서드가 지원되는지 확인
                                boolean supportsGenerateContent = false;
                                if (modelNode.has("supportedGenerationMethods")) {
                                    for (JsonNode method : modelNode.get("supportedGenerationMethods")) {
                                        if ("generateContent".equals(method.asText())) {
                                            supportsGenerateContent = true;
                                            break;
                                        }
                                    }
                                }

                                if (supportsGenerateContent) {
                                    Map<String, String> modelEntry = createModelEntry(modelName, version);
                                    // 중복 제거
                                    boolean exists = false;
                                    for (Map<String, String> existing : availableModels) {
                                        if (existing.get("model").equals(modelName) &&
                                            existing.get("version").equals(version)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        availableModels.add(modelEntry);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ListModels 실패 시 무시하고 계속
                continue;
            }
        }

        return availableModels.isEmpty() ? null : availableModels;
    }

    private boolean checkInternet() {
        try {
            System.out.println("[인터넷 연결 확인] 시작...");
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))  // 2초로 단축
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.google.com"))
                .timeout(Duration.ofSeconds(2))  // 2초로 단축
                .build();
            System.out.println("[인터넷 연결 확인] Google에 연결 시도 중...");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean isConnected = response.statusCode() == 200;
            System.out.println("[인터넷 연결 확인] 상태 코드: " + response.statusCode() + ", 연결 결과: " + (isConnected ? "성공" : "실패"));
            return isConnected;
        } catch (Exception e) {
            System.err.println("[인터넷 연결 확인] ❌ 실패: " + e.getClass().getName() + " - " + e.getMessage());
            return false;
        }
    }
}


