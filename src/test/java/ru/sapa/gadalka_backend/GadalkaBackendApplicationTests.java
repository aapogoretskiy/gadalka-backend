package ru.sapa.gadalka_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;

/**
 * Smoke-тест: поднимает весь Spring-контекст целиком.
 * Проверяет, что все бины собираются, зависимости разрешаются
 * и все Flyway-миграции проходят на чистой БД (в CI — контейнер Postgres 16).
 */
/*
 * Автоконфигурация telegram-стартера исключена: она сканирует контекст на бины-боты
 * и регистрирует их на long-polling. Наш мок GadalkaTelegramBot она тоже находит,
 * спрашивает у него токен, получает null (мок) и падает с NPE.
 * В тестах long-polling не нужен вообще.
 */
@SpringBootTest(properties =
		"spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration")
class GadalkaBackendApplicationTests {

	/**
	 * Telegram-бот подменяется моком: в CI он выключен (TELEGRAM_BOT_ENABLED=false),
	 * но 8 классов (AdminController, BroadcastService, PaymentService и др.) требуют
	 * его в конструкторе безусловно — без мока контекст не поднимется.
	 * TODO этап рефакторинга: сделать зависимость от бота опциональной (ObjectProvider),
	 * чтобы флаг telegram.bot.enabled=false работал и в проде.
	 */
	@MockitoBean
	private GadalkaTelegramBot telegramBot;

	/**
	 * TelegramClient создаётся в TelegramBotConfiguration, которая тоже выключена
	 * флагом telegram.bot.enabled — а NotificationSchedulerService и
	 * TelegramStarsService требуют его безусловно. Мокаем по той же причине.
	 */
	@MockitoBean
	private TelegramClient telegramClient;

	@Test
	void contextLoads() {
	}

}
