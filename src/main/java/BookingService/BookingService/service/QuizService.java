package BookingService.BookingService.service;

import BookingService.BookingService.dto.request.QuizAnswerRequest;
import BookingService.BookingService.dto.request.QuizQuestionRequest;
import BookingService.BookingService.dto.response.QuizAnswerResponse;
import BookingService.BookingService.dto.response.QuizQuestionResponse;
import BookingService.BookingService.entity.*;
import BookingService.BookingService.enums.SkinType;
import BookingService.BookingService.exception.AppException;
import BookingService.BookingService.exception.ErrorCode;
import BookingService.BookingService.repository.QuizAnswerRepository;
import BookingService.BookingService.repository.QuizQuestionRepository;
import BookingService.BookingService.repository.QuizResultRepository;
import BookingService.BookingService.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final QuizResultRepository quizResultRepository;
    private final UserRepository userRepository;
    private final ServiceEntityService serviceEntityService;

    public Map<String, Object> processQuiz(Map<Long, Long> selectedAnswers) {
        Map<SkinType, Integer> skinTypeScores = new HashMap<>();
        for (SkinType type : SkinType.values()) {
            skinTypeScores.put(type, 0);
        }

        List<Map<String, Object>> userResponses = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : selectedAnswers.entrySet()) {
            Long questionId = entry.getKey();
            Long answerId = entry.getValue();

            QuizAnswer answer = quizAnswerRepository.findById(answerId)
                    .orElseThrow(() -> new AppException(ErrorCode.QUIZ_ANSWER_NOT_FOUND));
            QuizQuestion question = quizQuestionRepository.findById(questionId)
                    .orElseThrow(() -> new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));

            SkinType skinType = determineSkinTypeFromAnswer(answer);
            int score = answer.getScore();

            skinTypeScores.put(skinType, skinTypeScores.get(skinType) + score);

            Map<String, Object> response = new HashMap<>();
            response.put("questionId", question.getId());
            response.put("questionText", question.getQuestion());
            response.put("answerId", answer.getId());
            response.put("answerText", answer.getAnswer());
            response.put("score", score);
            response.put("skinType", skinType.toString());
            userResponses.add(response);
        }

        SkinType detectedSkinType = Collections.max(skinTypeScores.entrySet(), Map.Entry.comparingByValue()).getKey();

        List<ServiceEntity> recommendedServices = serviceEntityService.getServicesBySkinType(detectedSkinType);
        String recommendedServiceNames = recommendedServices.stream()
                .map(ServiceEntity::getName)
                .collect(Collectors.joining(", "));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserEmail = authentication.getName();
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        QuizResult quizResult = QuizResult.builder()
                .user(user)
                .detectedSkinType(detectedSkinType)
                .recommendedService(recommendedServiceNames)
                .createdAt(LocalDateTime.now())
                .build();
        quizResultRepository.save(quizResult);

        Map<String, Object> result = new HashMap<>();
        result.put("userResponses", userResponses);
        result.put("skinTypeScores", skinTypeScores);
        result.put("detectedSkinType", detectedSkinType);
        result.put("recommendedServices", recommendedServices);
        return result;
    }

    private SkinType determineSkinTypeFromAnswer(QuizAnswer answer) {
        String answerText = answer.getAnswer();
        String questionText = answer.getQuestion().getQuestion();

        return switch (questionText) {
            case "Da bạn có thường xuyên bị bóng dầu không?" -> switch (answerText) {
                case "Luôn luôn" -> SkinType.OILY;
                case "Thỉnh thoảng" -> SkinType.COMBINATION;
                case "Hiếm khi" -> SkinType.NORMAL;
                case "Không bao giờ" -> SkinType.DRY;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Bạn cảm thấy da mình thế nào sau khi rửa mặt?" -> switch (answerText) {
                case "Căng rát" -> SkinType.DRY;
                case "Mềm mại, không khó chịu" -> SkinType.NORMAL;
                case "Dầu xuất hiện sau vài giờ" -> SkinType.OILY;
                case "Khô ở một số vùng, dầu ở vùng khác" -> SkinType.COMBINATION;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Bạn có dễ bị mụn không?" -> switch (answerText) {
                case "Thường xuyên" -> SkinType.OILY;
                case "Đôi khi, vào những thời điểm nhất định" -> SkinType.COMBINATION;
                case "Rất hiếm" -> SkinType.NORMAL;
                case "Hầu như không bao giờ" -> SkinType.DRY;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Lỗ chân lông của bạn có kích thước thế nào?" -> switch (answerText) {
                case "To và dễ thấy" -> SkinType.OILY;
                case "Nhỏ nhưng rõ ràng ở vùng chữ T" -> SkinType.COMBINATION;
                case "Nhỏ, khó thấy" -> SkinType.NORMAL;
                case "Rất nhỏ hoặc không thấy" -> SkinType.DRY;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Da bạn có bị bong tróc không?" -> switch (answerText) {
                case "Thường xuyên" -> SkinType.DRY;
                case "Đôi khi vào mùa đông" -> SkinType.NORMAL;
                case "Không bao giờ" -> SkinType.OILY;
                case "Chỉ ở một số vùng" -> SkinType.COMBINATION;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Bạn có dễ bị kích ứng, đỏ da không?" -> switch (answerText) {
                case "Rất dễ" -> SkinType.SENSITIVE;
                case "Thỉnh thoảng" -> SkinType.NORMAL;
                case "Hiếm khi" -> SkinType.OILY;
                case "Gần như không bao giờ" -> SkinType.COMBINATION;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Khi bạn thử sản phẩm dưỡng da mới, da bạn phản ứng thế nào?" -> switch (answerText) {
                case "Dễ bị kích ứng, đỏ" -> SkinType.SENSITIVE;
                case "Cần thời gian thích nghi" -> SkinType.NORMAL;
                case "Không có phản ứng" -> SkinType.OILY;
                case "Chỉ phản ứng với một số thành phần" -> SkinType.COMBINATION;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Bạn có thấy da mình thay đổi theo thời tiết không?" -> switch (answerText) {
                case "Rất nhạy cảm với thời tiết" -> SkinType.SENSITIVE;
                case "Chỉ thay đổi nhẹ" -> SkinType.NORMAL;
                case "Mùa hè nhiều dầu, mùa đông khô" -> SkinType.COMBINATION;
                case "Không ảnh hưởng nhiều" -> SkinType.OILY;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Bạn có cần dưỡng ẩm hàng ngày không?" -> switch (answerText) {
                case "Không thể thiếu" -> SkinType.DRY;
                case "Cần nhưng không nhiều" -> SkinType.NORMAL;
                case "Chỉ vùng khô" -> SkinType.COMBINATION;
                case "Không cần hoặc rất ít" -> SkinType.OILY;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            case "Sau 6 tiếng không rửa mặt, da bạn trông thế nào?" -> switch (answerText) {
                case "Rất dầu, bóng nhờn" -> SkinType.OILY;
                case "Dầu ở vùng chữ T" -> SkinType.COMBINATION;
                case "Bình thường" -> SkinType.NORMAL;
                case "Căng khô" -> SkinType.DRY;
                default -> throw new IllegalArgumentException("Unknown answer: " + answerText);
            };
            default -> throw new IllegalArgumentException("Unknown question: " + questionText);
        };
    }

    public List<QuizResult> getUserQuizHistory(User user) {
        return quizResultRepository.findByUser(user);
    }

    public void populateQuizData() {
        if (quizQuestionRepository.count() > 0) {
            return;
        }

        List<QuizQuestion> questions = List.of(
                new QuizQuestion(null, "Da bạn có thường xuyên bị bóng dầu không?", null),
                new QuizQuestion(null, "Bạn cảm thấy da mình thế nào sau khi rửa mặt?", null),
                new QuizQuestion(null, "Bạn có dễ bị mụn không?", null),
                new QuizQuestion(null, "Lỗ chân lông của bạn có kích thước thế nào?", null),
                new QuizQuestion(null, "Da bạn có bị bong tróc không?", null),
                new QuizQuestion(null, "Bạn có dễ bị kích ứng, đỏ da không?", null),
                new QuizQuestion(null, "Khi bạn thử sản phẩm dưỡng da mới, da bạn phản ứng thế nào?", null),
                new QuizQuestion(null, "Bạn có thấy da mình thay đổi theo thời tiết không?", null),
                new QuizQuestion(null, "Bạn có cần dưỡng ẩm hàng ngày không?", null),
                new QuizQuestion(null, "Sau 6 tiếng không rửa mặt, da bạn trông thế nào?", null)
        );

        quizQuestionRepository.saveAll(questions);

        List<QuizAnswer> answers = List.of(
                new QuizAnswer(null, "Luôn luôn", 3, questions.get(0)),
                new QuizAnswer(null, "Thỉnh thoảng", 2, questions.get(0)),
                new QuizAnswer(null, "Hiếm khi", 1, questions.get(0)),
                new QuizAnswer(null, "Không bao giờ", 0, questions.get(0)),
                new QuizAnswer(null, "Căng rát", 3, questions.get(1)),
                new QuizAnswer(null, "Mềm mại, không khó chịu", 2, questions.get(1)),
                new QuizAnswer(null, "Dầu xuất hiện sau vài giờ", 3, questions.get(1)),
                new QuizAnswer(null, "Khô ở một số vùng, dầu ở vùng khác", 2, questions.get(1)),
                new QuizAnswer(null, "Thường xuyên", 3, questions.get(2)),
                new QuizAnswer(null, "Đôi khi, vào những thời điểm nhất định", 2, questions.get(2)),
                new QuizAnswer(null, "Rất hiếm", 1, questions.get(2)),
                new QuizAnswer(null, "Hầu như không bao giờ", 0, questions.get(2)),
                new QuizAnswer(null, "To và dễ thấy", 3, questions.get(3)),
                new QuizAnswer(null, "Nhỏ nhưng rõ ràng ở vùng chữ T", 2, questions.get(3)),
                new QuizAnswer(null, "Nhỏ, khó thấy", 1, questions.get(3)),
                new QuizAnswer(null, "Rất nhỏ hoặc không thấy", 0, questions.get(3)),
                new QuizAnswer(null, "Thường xuyên", 3, questions.get(4)),
                new QuizAnswer(null, "Đôi khi vào mùa đông", 2, questions.get(4)),
                new QuizAnswer(null, "Không bao giờ", 0, questions.get(4)),
                new QuizAnswer(null, "Chỉ ở một số vùng", 2, questions.get(4)),
                new QuizAnswer(null, "Rất dễ", 3, questions.get(5)),
                new QuizAnswer(null, "Thỉnh thoảng", 2, questions.get(5)),
                new QuizAnswer(null, "Hiếm khi", 1, questions.get(5)),
                new QuizAnswer(null, "Gần như không bao giờ", 0, questions.get(5)),
                new QuizAnswer(null, "Dễ bị kích ứng, đỏ", 3, questions.get(6)),
                new QuizAnswer(null, "Cần thời gian thích nghi", 2, questions.get(6)),
                new QuizAnswer(null, "Không có phản ứng", 1, questions.get(6)),
                new QuizAnswer(null, "Chỉ phản ứng với một số thành phần", 2, questions.get(6)),
                new QuizAnswer(null, "Rất nhạy cảm với thời tiết", 3, questions.get(7)),
                new QuizAnswer(null, "Chỉ thay đổi nhẹ", 2, questions.get(7)),
                new QuizAnswer(null, "Mùa hè nhiều dầu, mùa đông khô", 3, questions.get(7)),
                new QuizAnswer(null, "Không ảnh hưởng nhiều", 1, questions.get(7)),
                new QuizAnswer(null, "Không thể thiếu", 3, questions.get(8)),
                new QuizAnswer(null, "Cần nhưng không nhiều", 2, questions.get(8)),
                new QuizAnswer(null, "Chỉ vùng khô", 2, questions.get(8)),
                new QuizAnswer(null, "Không cần hoặc rất ít", 1, questions.get(8)),
                new QuizAnswer(null, "Rất dầu, bóng nhờn", 3, questions.get(9)),
                new QuizAnswer(null, "Dầu ở vùng chữ T", 2, questions.get(9)),
                new QuizAnswer(null, "Bình thường", 2, questions.get(9)),
                new QuizAnswer(null, "Căng khô", 3, questions.get(9))
        );

        quizAnswerRepository.saveAll(answers);
    }

    public List<Map<String, Object>> getQuizQuestionsWithAnswers() {
        List<QuizQuestion> questions = quizQuestionRepository.findAll();
        if (questions.isEmpty()) {
            throw new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND);
        }

        return questions.stream()
                .map(question -> {
                    List<QuizAnswer> answers = quizAnswerRepository.findByQuestion(question);
                    if (answers.isEmpty()) {
                        throw new AppException(ErrorCode.QUIZ_ANSWER_NOT_FOUND);
                    }

                    Map<String, Object> questionData = new HashMap<>();
                    questionData.put("questionId", question.getId());
                    questionData.put("questionText", question.getQuestion());

                    List<Map<String, Object>> answerList = answers.stream()
                            .map(answer -> {
                                Map<String, Object> answerData = new HashMap<>();
                                answerData.put("answerId", answer.getId());
                                answerData.put("answerText", answer.getAnswer());
                                answerData.put("score", answer.getScore());
                                return answerData;
                            })
                            .collect(Collectors.toList());

                    questionData.put("answers", answerList);
                    return questionData;
                })
                .collect(Collectors.toList());
    }

    public List<ServiceEntity> getRecommendedServicesFromLatestQuiz() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserEmail = authentication.getName();
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        List<QuizResult> quizHistory = quizResultRepository.findByUser(user);
        if (quizHistory.isEmpty()) {
            throw new AppException(ErrorCode.QUIZ_RESULT_NOT_FOUND);
        }

        QuizResult latestQuiz = quizHistory.stream()
                .sorted(Comparator.comparing(QuizResult::getCreatedAt).reversed())
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_RESULT_NOT_FOUND));

        SkinType detectedSkinType = latestQuiz.getDetectedSkinType();
        return serviceEntityService.getServicesBySkinType(detectedSkinType);
    }

    // --- CRUD cho QuizQuestion ---

    public QuizQuestionResponse createQuestion(QuizQuestionRequest request) {
        if (request.getAnswers() == null || request.getAnswers().size() != 4) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        QuizQuestion quizQuestion = QuizQuestion.builder()
                .question(request.getQuestionText())
                .skinType(request.getSkinType())
                .build();
        QuizQuestion savedQuestion = quizQuestionRepository.save(quizQuestion);

        List<QuizAnswer> answers = request.getAnswers().stream().map(answerRequest -> {
            QuizAnswer quizAnswer = QuizAnswer.builder()
                    .answer(answerRequest.getAnswerText())
                    .score(answerRequest.getScore())
                    .question(savedQuestion)
                    .build();
            return quizAnswer;
        }).collect(Collectors.toList());
        quizAnswerRepository.saveAll(answers);

        QuizQuestionResponse response = new QuizQuestionResponse();
        response.setId(savedQuestion.getId());
        response.setQuestionText(savedQuestion.getQuestion());
        response.setSkinType(savedQuestion.getSkinType());

        List<QuizAnswerResponse> answerResponses = answers.stream().map(answer -> {
            QuizAnswerResponse answerResponse = new QuizAnswerResponse();
            answerResponse.setId(answer.getId());
            answerResponse.setAnswerText(answer.getAnswer());
            answerResponse.setScore(answer.getScore());
            return answerResponse;
        }).collect(Collectors.toList());
        response.setAnswers(answerResponses);

        return response;
    }

    public List<QuizQuestionResponse> getAllQuestions() {
        return quizQuestionRepository.findAll().stream()
                .map(question -> {
                    QuizQuestionResponse response = new QuizQuestionResponse();
                    response.setId(question.getId());
                    response.setQuestionText(question.getQuestion());
                    response.setSkinType(question.getSkinType());

                    List<QuizAnswer> answers = quizAnswerRepository.findByQuestion(question);
                    List<QuizAnswerResponse> answerResponses = answers.stream().map(answer -> {
                        QuizAnswerResponse answerResponse = new QuizAnswerResponse();
                        answerResponse.setId(answer.getId());
                        answerResponse.setAnswerText(answer.getAnswer());
                        answerResponse.setScore(answer.getScore());
                        return answerResponse;
                    }).collect(Collectors.toList());
                    response.setAnswers(answerResponses);

                    return response;
                })
                .collect(Collectors.toList());
    }

    public QuizQuestionResponse getQuestionById(Long id) {
        QuizQuestion question = quizQuestionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));

        QuizQuestionResponse response = new QuizQuestionResponse();
        response.setId(question.getId());
        response.setQuestionText(question.getQuestion());
        response.setSkinType(question.getSkinType());

        List<QuizAnswer> answers = quizAnswerRepository.findByQuestion(question);
        List<QuizAnswerResponse> answerResponses = answers.stream().map(answer -> {
            QuizAnswerResponse answerResponse = new QuizAnswerResponse();
            answerResponse.setId(answer.getId());
            answerResponse.setAnswerText(answer.getAnswer());
            answerResponse.setScore(answer.getScore());
            return answerResponse;
        }).collect(Collectors.toList());
        response.setAnswers(answerResponses);

        return response;
    }

    public QuizQuestionResponse updateQuestion(Long id, QuizQuestionRequest request) {
        QuizQuestion quizQuestion = quizQuestionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));

        quizQuestion.setQuestion(request.getQuestionText());
        quizQuestion.setSkinType(request.getSkinType());
        QuizQuestion updatedQuestion = quizQuestionRepository.save(quizQuestion);

        List<QuizAnswer> existingAnswers = quizAnswerRepository.findByQuestion(quizQuestion);
        quizAnswerRepository.deleteAll(existingAnswers);

        List<QuizAnswer> newAnswers = request.getAnswers().stream().map(answerRequest -> {
            QuizAnswer quizAnswer = QuizAnswer.builder()
                    .answer(answerRequest.getAnswerText())
                    .score(answerRequest.getScore())
                    .question(updatedQuestion)
                    .build();
            return quizAnswer;
        }).collect(Collectors.toList());
        quizAnswerRepository.saveAll(newAnswers);

        QuizQuestionResponse response = new QuizQuestionResponse();
        response.setId(updatedQuestion.getId());
        response.setQuestionText(updatedQuestion.getQuestion());
        response.setSkinType(updatedQuestion.getSkinType());

        List<QuizAnswerResponse> answerResponses = newAnswers.stream().map(answer -> {
            QuizAnswerResponse answerResponse = new QuizAnswerResponse();
            answerResponse.setId(answer.getId());
            answerResponse.setAnswerText(answer.getAnswer());
            answerResponse.setScore(answer.getScore());
            return answerResponse;
        }).collect(Collectors.toList());
        response.setAnswers(answerResponses);

        return response;
    }

    public void deleteQuestion(Long id) {
        QuizQuestion quizQuestion = quizQuestionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));

        List<QuizAnswer> relatedAnswers = quizAnswerRepository.findByQuestion(quizQuestion);
        if (!relatedAnswers.isEmpty()) {
            quizAnswerRepository.deleteAll(relatedAnswers);
        }
        quizQuestionRepository.deleteById(id);
    }
}