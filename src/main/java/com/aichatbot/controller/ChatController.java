package com.aichatbot.controller;

import com.aichatbot.model.ChatCategory;
import com.aichatbot.model.ChatRequest;
import com.aichatbot.model.ChatResponse;
import com.aichatbot.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class ChatController {

    private static final String ATTR_CATEGORIES = "chatCategories";
    private static final String ATTR_CURRENT_CAT = "currentCategoryId";

    @Autowired
    private AiService aiService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @SuppressWarnings("unchecked")
    private List<ChatCategory> ensureCategories(HttpSession session) {
        List<ChatCategory> categories = (List<ChatCategory>) session.getAttribute(ATTR_CATEGORIES);
        if (categories == null) {
            categories = new ArrayList<>();
            List<Map<String, String>> legacy = null;
            try {
                legacy = (List<Map<String, String>>) session.getAttribute("messages");
            } catch (Exception ignored) {
                // ignore
            }
            if (legacy != null && !legacy.isEmpty()) {
                categories.add(new ChatCategory(UUID.randomUUID().toString(), "일반", new ArrayList<>(legacy)));
            } else {
                categories.add(new ChatCategory(UUID.randomUUID().toString(), "일반", new ArrayList<>()));
            }
            session.setAttribute(ATTR_CATEGORIES, categories);
            session.setAttribute(ATTR_CURRENT_CAT, categories.get(0).getId());
            session.removeAttribute("messages");
        } else if (session.getAttribute(ATTR_CURRENT_CAT) == null && !categories.isEmpty()) {
            session.setAttribute(ATTR_CURRENT_CAT, categories.get(0).getId());
        }
        return categories;
    }

    private ChatCategory getCurrentCategory(HttpSession session) {
        List<ChatCategory> list = ensureCategories(session);
        String id = (String) session.getAttribute(ATTR_CURRENT_CAT);
        if (id != null) {
            for (ChatCategory c : list) {
                if (c.getId().equals(id)) {
                    return c;
                }
            }
        }
        if (!list.isEmpty()) {
            session.setAttribute(ATTR_CURRENT_CAT, list.get(0).getId());
            return list.get(0);
        }
        ChatCategory created = new ChatCategory(UUID.randomUUID().toString(), "일반", new ArrayList<>());
        list.add(created);
        session.setAttribute(ATTR_CURRENT_CAT, created.getId());
        return created;
    }

    private ChatCategory findCategoryById(HttpSession session, String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return null;
        }
        List<ChatCategory> list = ensureCategories(session);
        for (ChatCategory c : list) {
            if (c.getId().equals(categoryId)) {
                return c;
            }
        }
        return null;
    }

    private void attachCategoryMeta(ChatResponse res, ChatCategory cat) {
        if (cat != null) {
            res.setCategoryId(cat.getId());
            res.setCategoryTitle(cat.getTitle());
        }
    }

    /** 기본 이름일 때만 AI 맥락 요약으로 범주명 자동 설정 */
    private boolean shouldAutoRenameCategoryTitle(ChatCategory cat) {
        if (cat == null || cat.isSkipAutoTitle()) {
            return false;
        }
        String t = cat.getTitle();
        if (t == null) {
            return true;
        }
        t = t.trim();
        return t.isEmpty() || "일반".equals(t) || "새 범주".equals(t);
    }

    private void maybeApplyAiCategoryTitle(ChatCategory currentCat, List<Map<String, String>> messagesList,
                                          String aiResponse, ChatRequest request) {
        if (currentCat == null || messagesList == null || request == null) {
            return;
        }
        if (aiResponse != null && aiResponse.trim().startsWith("❌")) {
            return;
        }
        if (!shouldAutoRenameCategoryTitle(currentCat) || messagesList.size() < 2) {
            return;
        }
        try {
            String suggested = aiService.suggestCategoryTitleFromConversation(
                new ArrayList<>(messagesList),
                request.getAiType(),
                request.getModel(),
                request.getGeminiApiKey(),
                request.getOpenaiApiKey(),
                Math.min(0.5, request.getTemperature())
            );
            if (suggested != null && !suggested.isBlank()) {
                currentCat.setTitle(suggested.trim());
            }
        } catch (Exception ex) {
            System.err.println("범주 자동 제목 설정 실패: " + ex.getMessage());
        }
    }

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        ensureCategories(session);
        model.addAttribute("aiTypes", List.of(
            "Google Gemini (무료)",
            "OpenAI ChatGPT"
        ));
        model.addAttribute("geminiModels", List.of(
            "gemini-pro",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        ));
        model.addAttribute("openaiModels", List.of(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-mini"
        ));
        return "index";
    }

    @PostMapping("/chat")
    @ResponseBody
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpSession session) {
        System.out.println("\n========== /chat 엔드포인트 호출 ==========");

        ChatCategory currentCat = getCurrentCategory(session);

        if (request == null) {
            System.err.println("❌ 요청이 null입니다.");
            ChatResponse bad = new ChatResponse("❌ 잘못된 요청입니다. 요청 본문이 비어있습니다.", false);
            attachCategoryMeta(bad, currentCat);
            return ResponseEntity.ok(bad);
        }

        try {
            System.out.println("요청 받음 - AI 타입: " + (request.getAiType() != null ? request.getAiType() : "null"));
            System.out.println("프롬프트: " + (request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())) + "..." : "null"));
            System.out.println("모델: " + (request.getModel() != null ? request.getModel() : "null"));
        } catch (Exception e) {
            System.err.println("❌ 요청 정보 출력 중 오류: " + e.getMessage());
        }

        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            System.err.println("❌ 프롬프트가 null이거나 비어있습니다.");
            ChatResponse bad = new ChatResponse("❌ 메시지를 입력해주세요.", false);
            attachCategoryMeta(bad, currentCat);
            return ResponseEntity.ok(bad);
        }

        List<Map<String, String>> messagesList = currentCat.getMessages();
        if (messagesList == null) {
            messagesList = new ArrayList<>();
            currentCat.setMessages(messagesList);
        }

        try {
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", request.getPrompt());
            messagesList.add(userMessage);
        } catch (Exception e) {
            System.err.println("❌ 사용자 메시지 추가 실패: " + e.getMessage());
        }

        final List<Map<String, String>> conversationSnapshot = new ArrayList<>(messagesList);

        String response = null;
        Thread progressLogger = null;
        try {
            System.out.println("AI 서비스 호출 시작...");
            long startTime = System.currentTimeMillis();

            progressLogger = new Thread(() -> {
                try {
                    int count = 0;
                    while (!Thread.currentThread().isInterrupted() && count < 3) {
                        Thread.sleep(20000);
                        long elapsed = System.currentTimeMillis() - startTime;
                        System.out.println("⏳ AI 서비스 처리 중... (" + (elapsed / 1000) + "초 경과)");
                        count++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            progressLogger.setDaemon(true);
            progressLogger.start();

            try {
                CompletableFuture<String> futureResponse = CompletableFuture.supplyAsync(() -> {
                    try {
                        System.out.println("CompletableFuture 내부에서 AI 서비스 호출 시작...");
                        return aiService.getResponse(
                            request.getAiType(),
                            request.getPrompt(),
                            conversationSnapshot,
                            request.getModel(),
                            request.getGeminiApiKey(),
                            request.getOpenaiApiKey(),
                            request.getTemperature()
                        );
                    } catch (Throwable e) {
                        System.err.println("❌ AI 서비스에서 예외 발생: " + e.getClass().getName() + " - " + e.getMessage());
                        e.printStackTrace();
                        return "❌ AI 서비스 오류가 발생했습니다.\n\n" +
                               "오류: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + "\n\n" +
                               "💡 해결 방법:\n" +
                               "1. 🔄 페이지 새로고침 후 다시 시도\n" +
                               "2. 🌐 인터넷 연결 확인\n" +
                               "3. ⏰ 잠시 후 다시 시도";
                    }
                }, executorService).exceptionally(throwable -> {
                    System.err.println("❌ CompletableFuture에서 예외 발생: " + throwable.getClass().getName() + " - " + throwable.getMessage());
                    throwable.printStackTrace();
                    return "❌ 요청 처리 중 오류가 발생했습니다.\n\n" +
                           "오류: " + (throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName()) + "\n\n" +
                           "💡 해결 방법:\n" +
                           "1. 🔄 페이지 새로고침 후 다시 시도\n" +
                           "2. 🌐 인터넷 연결 확인\n" +
                           "3. ⏰ 잠시 후 다시 시도";
                });

                try {
                    response = futureResponse.get(120, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    System.err.println("⏱️ AI 서비스 호출 타임아웃 (120초 초과)");
                    futureResponse.cancel(true);
                    response = "⏱️ 응답 시간이 초과되었습니다.\n\n" +
                              "시도 시간: 120초\n\n" +
                              "모델 연결에 시간이 오래 걸리고 있습니다.\n" +
                              "기본 응답 모드로 전환합니다.\n\n" +
                              "💡 해결 방법:\n" +
                              "1. ⏰ 잠시 후 다시 시도 (모델 로딩 완료 대기)\n" +
                              "2. 🔄 페이지 새로고침\n" +
                              "3. 🌐 인터넷 연결 확인\n" +
                              "4. 서버 콘솔 로그 확인 (상세 오류 정보)";
                } catch (Exception e) {
                    System.err.println("❌ CompletableFuture에서 예외 발생: " + e.getClass().getName() + " - " + e.getMessage());
                    e.printStackTrace();
                    futureResponse.cancel(true);
                    response = "❌ 요청 처리 중 오류가 발생했습니다.\n\n" +
                              "오류: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + "\n\n" +
                              "💡 해결 방법:\n" +
                              "1. 🔄 페이지 새로고침 후 다시 시도\n" +
                              "2. 🌐 인터넷 연결 확인\n" +
                              "3. ⏰ 잠시 후 다시 시도";
                }
            } catch (Throwable serviceException) {
                System.err.println("❌ AI 서비스 호출 중 심각한 예외 발생:");
                serviceException.printStackTrace();
                response = "❌ AI 서비스 오류가 발생했습니다.\n\n" +
                          "오류: " + (serviceException.getMessage() != null ? serviceException.getMessage() : serviceException.getClass().getSimpleName()) + "\n\n" +
                          "💡 해결 방법:\n" +
                          "1. 🔄 페이지 새로고침 후 다시 시도\n" +
                          "2. 🌐 인터넷 연결 확인\n" +
                          "3. ⏰ 잠시 후 다시 시도";
            } finally {
                if (progressLogger != null) {
                    progressLogger.interrupt();
                }
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            System.out.println("AI 서비스 응답 받음 (소요 시간: " + elapsedTime + "ms)");

            if (response == null || response.trim().isEmpty()) {
                System.err.println("⚠️ 응답이 null이거나 비어있음");
                response = "❌ AI 모델 연결에 실패했습니다.\n\n" +
                          "시도 시간: " + (elapsedTime / 1000) + "초\n\n" +
                          "💡 해결 방법:\n" +
                          "1. ⏰ 잠시 후 다시 시도\n" +
                          "2. 🔄 페이지 새로고침\n" +
                          "3. 🌐 인터넷 연결 확인";
            }

            Map<String, String> assistantMessage = new HashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", response);
            messagesList.add(assistantMessage);

            maybeApplyAiCategoryTitle(currentCat, messagesList, response, request);

            ChatResponse ok = new ChatResponse(response, true, messagesList.size());
            attachCategoryMeta(ok, currentCat);
            return ResponseEntity.ok(ok);

        } catch (Throwable e) {
            System.err.println("\n❌ /chat 엔드포인트에서 심각한 예외 발생:");
            e.printStackTrace();

            String errorMsg = "❌ 서버 오류가 발생했습니다.\n\n" +
                             "오류: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + "\n\n" +
                             "💡 해결 방법:\n" +
                             "1. 🔄 페이지 새로고침 후 다시 시도\n" +
                             "2. 🌐 인터넷 연결 확인\n" +
                             "3. ⏰ 잠시 후 다시 시도\n" +
                             "4. 서버 콘솔 로그 확인";

            try {
                if (progressLogger != null && progressLogger.isAlive()) {
                    progressLogger.interrupt();
                }
            } catch (Exception ex) {
                System.err.println("progressLogger 중단 실패: " + ex.getMessage());
            }

            try {
                if (messagesList != null) {
                    Map<String, String> errorMessage = new HashMap<>();
                    errorMessage.put("role", "assistant");
                    errorMessage.put("content", errorMsg);
                    messagesList.add(errorMessage);
                }
            } catch (Exception ex) {
                System.err.println("메시지 추가 실패: " + ex.getMessage());
            }

            ChatResponse err = new ChatResponse(errorMsg, false, messagesList != null ? messagesList.size() : 0);
            attachCategoryMeta(err, currentCat);
            try {
                return ResponseEntity.ok(err);
            } catch (Exception ex) {
                return ResponseEntity.status(500).body(new ChatResponse("❌ 서버 오류가 발생했습니다.", false));
            }
        }
    }

    @GetMapping("/chat/history")
    @ResponseBody
    public Map<String, Object> chatHistory(HttpSession session) {
        ChatCategory cat = getCurrentCategory(session);
        List<Map<String, String>> list = cat.getMessages();
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", cat.getId());
        body.put("categoryTitle", cat.getTitle());
        if (list == null || list.isEmpty()) {
            body.put("messages", Collections.emptyList());
            body.put("count", 0);
            return body;
        }
        body.put("messages", new ArrayList<>(list));
        body.put("count", list.size());
        return body;
    }

    @GetMapping("/chat/categories")
    @ResponseBody
    public Map<String, Object> listCategories(HttpSession session) {
        List<ChatCategory> categories = ensureCategories(session);
        String currentId = (String) session.getAttribute(ATTR_CURRENT_CAT);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChatCategory c : categories) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.getId());
            row.put("title", c.getTitle());
            int n = c.getMessages() != null ? c.getMessages().size() : 0;
            row.put("messageCount", n);
            row.put("current", c.getId().equals(currentId));
            rows.add(row);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("categories", rows);
        body.put("currentCategoryId", currentId);
        return body;
    }

    @PostMapping("/chat/categories")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody(required = false) Map<String, String> body,
                                                                HttpSession session) {
        ensureCategories(session);
        String raw = body != null && body.get("title") != null ? body.get("title").trim() : "";
        String title = raw.isEmpty() ? "새 범주" : raw;
        boolean userChoseName = !raw.isEmpty() && !"새 범주".equals(raw);
        ChatCategory created = new ChatCategory(UUID.randomUUID().toString(), title, new ArrayList<>());
        created.setSkipAutoTitle(userChoseName);
        @SuppressWarnings("unchecked")
        List<ChatCategory> categories = (List<ChatCategory>) session.getAttribute(ATTR_CATEGORIES);
        categories.add(created);
        session.setAttribute(ATTR_CURRENT_CAT, created.getId());
        return ResponseEntity.ok(listCategories(session));
    }

    @PostMapping("/chat/category/select")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> selectCategory(@RequestBody Map<String, String> body,
                                                              HttpSession session) {
        ensureCategories(session);
        String id = body != null ? body.get("categoryId") : null;
        if (id == null || id.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "categoryId가 필요합니다.");
            return ResponseEntity.badRequest().body(err);
        }
        ChatCategory found = findCategoryById(session, id);
        if (found == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "범주를 찾을 수 없습니다.");
            return ResponseEntity.badRequest().body(err);
        }
        session.setAttribute(ATTR_CURRENT_CAT, id);
        Map<String, Object> payload = listCategories(session);
        List<Map<String, String>> msgs = found.getMessages() != null ? found.getMessages() : Collections.emptyList();
        payload.put("categoryId", found.getId());
        payload.put("categoryTitle", found.getTitle());
        payload.put("messages", new ArrayList<>(msgs));
        payload.put("count", msgs.size());
        return ResponseEntity.ok(payload);
    }

    @DeleteMapping("/chat/categories/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable String id, HttpSession session) {
        List<ChatCategory> categories = ensureCategories(session);
        ChatCategory target = findCategoryById(session, id);
        if (target == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "범주를 찾을 수 없습니다.");
            return ResponseEntity.badRequest().body(err);
        }
        String current = (String) session.getAttribute(ATTR_CURRENT_CAT);
        if (id.equals(current)) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "현재 범주는 범주 자체를 삭제할 수 없습니다. 대신 '대화 삭제'를 사용하세요.");
            return ResponseEntity.badRequest().body(err);
        }
        if (categories.size() <= 1) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "범주가 하나뿐이면 삭제할 수 없습니다. 대화 기록 초기화를 사용하세요.");
            return ResponseEntity.badRequest().body(err);
        }
        categories.removeIf(c -> c.getId().equals(id));
        return ResponseEntity.ok(listCategories(session));
    }

    @PostMapping("/chat/categories/{id}/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearCategoryConversation(@PathVariable String id, HttpSession session) {
        ensureCategories(session);
        ChatCategory target = findCategoryById(session, id);
        if (target == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "범주를 찾을 수 없습니다.");
            return ResponseEntity.badRequest().body(err);
        }
        if (target.getMessages() != null) {
            target.getMessages().clear();
        }
        // currentCategoryId는 유지 (화면은 필요 시 프론트가 다시 로드)
        return ResponseEntity.ok(listCategories(session));
    }

    @PostMapping("/clear")
    @ResponseBody
    public ResponseEntity<Map<String, String>> clear(HttpSession session) {
        ChatCategory cat = getCurrentCategory(session);
        if (cat.getMessages() != null) {
            cat.getMessages().clear();
        }
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat/message/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteMessage(HttpSession session, @RequestBody Map<String, Object> body) {
        ChatCategory cat = getCurrentCategory(session);
        List<Map<String, String>> messages = cat.getMessages();
        if (messages == null) {
            messages = new ArrayList<>();
            cat.setMessages(messages);
        }

        if (body == null || !body.containsKey("index")) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "index가 필요합니다.");
            return ResponseEntity.badRequest().body(err);
        }

        int index;
        Object rawIndex = body.get("index");
        if (rawIndex instanceof Number) {
            index = ((Number) rawIndex).intValue();
        } else {
            try {
                index = Integer.parseInt(String.valueOf(rawIndex));
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "index 형식이 올바르지 않습니다.");
                return ResponseEntity.badRequest().body(err);
            }
        }

        if (index < 0 || index >= messages.size()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "삭제할 메시지를 찾을 수 없습니다.");
            return ResponseEntity.badRequest().body(err);
        }

        messages.remove(index);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", new ArrayList<>(messages));
        payload.put("count", messages.size());
        payload.put("categoryId", cat.getId());
        payload.put("categoryTitle", cat.getTitle());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/chat/message/move")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> moveMessage(HttpSession session, @RequestBody Map<String, Object> body) {
        ChatCategory source = getCurrentCategory(session);
        List<Map<String, String>> sourceMessages = source.getMessages();
        if (sourceMessages == null) {
            sourceMessages = new ArrayList<>();
            source.setMessages(sourceMessages);
        }

        if (body == null || !body.containsKey("index") || !body.containsKey("targetCategoryId")) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "index와 targetCategoryId가 필요합니다.");
            return ResponseEntity.badRequest().body(err);
        }

        int index;
        Object rawIndex = body.get("index");
        if (rawIndex instanceof Number) {
            index = ((Number) rawIndex).intValue();
        } else {
            try {
                index = Integer.parseInt(String.valueOf(rawIndex));
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "index 형식이 올바르지 않습니다.");
                return ResponseEntity.badRequest().body(err);
            }
        }
        if (index < 0 || index >= sourceMessages.size()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "이동할 메시지를 찾을 수 없습니다.");
            return ResponseEntity.badRequest().body(err);
        }

        String targetId = String.valueOf(body.get("targetCategoryId"));
        ChatCategory target = findCategoryById(session, targetId);
        if (target == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "대상 범주를 찾을 수 없습니다.");
            return ResponseEntity.badRequest().body(err);
        }
        if (source.getId().equals(target.getId())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "같은 범주로는 이동할 수 없습니다.");
            return ResponseEntity.badRequest().body(err);
        }

        Map<String, String> moving = sourceMessages.remove(index);
        List<Map<String, String>> targetMessages = target.getMessages();
        if (targetMessages == null) {
            targetMessages = new ArrayList<>();
            target.setMessages(targetMessages);
        }
        targetMessages.add(moving);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", new ArrayList<>(sourceMessages));
        payload.put("count", sourceMessages.size());
        payload.put("categoryId", source.getId());
        payload.put("categoryTitle", source.getTitle());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/shutdown")
    @ResponseBody
    public ResponseEntity<Map<String, String>> shutdown() {
        System.out.println("\n========== 서버 종료 요청 받음 ==========");
        Map<String, String> response = new HashMap<>();
        response.put("status", "shutting_down");
        response.put("message", "서버 종료 중...");
        new Thread(() -> {
            try {
                Thread.sleep(500);
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.exit(0);
            }
        }).start();
        return ResponseEntity.ok(response);
    }
}
