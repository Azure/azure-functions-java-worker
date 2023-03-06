package example.hello;


import example.hello.model.Greeting;
import example.hello.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class HelloTest {

    @Test
    public void test() {
        Greeting result = new Hello().apply(new User("foo"));
        assertThat(result.getMessage()).isEqualTo("Hello, foo!\n");
    }
}
