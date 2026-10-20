package dev.servicelog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long")
class ServicelogApplicationTests {

	@Test
	void contextLoads() {
	}

}
