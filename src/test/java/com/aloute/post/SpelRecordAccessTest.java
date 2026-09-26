package com.aloute.post;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Template Thymeleaf viết {@code ${post.author.username}} lên các record ({@code PostView}, {@code AuthorView}...).
 * Test này chốt rằng SpEL của phiên bản Spring đang dùng đọc được thuộc tính của record, kể cả record lồng nhau
 * và thuộc tính boolean; nếu nâng cấp Spring làm hỏng điều này thì test đỏ ngay chứ không đợi tới lúc render trang.
 */
class SpelRecordAccessTest {

    record Author(String username) {
    }

    record View(Author author, boolean mine, java.util.List<String> media) {
        boolean hasMedia() {
            return !media.isEmpty();
        }
    }

    private static Object eval(String expression, Object root) {
        return new SpelExpressionParser().parseExpression(expression).getValue(new StandardEvaluationContext(root));
    }

    @Test
    void readsNestedRecordComponents() {
        View view = new View(new Author("mochi"), true, java.util.List.of("a"));

        assertThat(eval("author.username", view)).isEqualTo("mochi");
        assertThat(eval("mine", view)).isEqualTo(true);
        assertThat(eval("media.size()", view)).isEqualTo(1);
    }
}
