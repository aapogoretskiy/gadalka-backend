-- Обновление вопросов категории "Близость" (intimacy):
-- делаем формулировки более откровенными, явно указывающими на интимную/сексуальную сферу.

UPDATE question_presets
SET question_text = 'Чего партнёр хочет от меня в постели, но не решается сказать?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 1;

UPDATE question_presets
SET question_text = 'Доволен(а) ли партнёр нашей сексуальной жизнью?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 2;

UPDATE question_presets
SET question_text = 'Что мешает нашей сексуальной совместимости?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 3;

UPDATE question_presets
SET question_text = 'О каких интимных желаниях партнёр молчит?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 4;

UPDATE question_presets
SET question_text = 'Как разжечь страсть и желание в наших отношениях?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 5;

UPDATE question_presets
SET question_text = 'Насколько сильно партнёр желает меня прямо сейчас?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 6;

UPDATE question_presets
SET question_text = 'Совместимы ли мы в постели?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 7;

UPDATE question_presets
SET question_text = 'Что карты советуют для улучшения нашей интимной жизни?'
WHERE category_id = (SELECT id FROM question_categories WHERE code = 'intimacy')
  AND sort_order = 8;
