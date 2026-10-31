package org.sprautestudio.spraute_engine.editor.project;

/** Starter content for new scripts and the demo project. */
public final class ScriptTemplates {
    private ScriptTemplates() {}

    public static String newScript(int id, String title) {
        return """
                script %d "%s"

                // Персонажи и точки
                character alice = "spraute:npc" name "Алиса"
                location start = (0, 64, 0)

                label begin
                spawn alice at start
                speaker 1 alice
                // say ждёт, пока игрок нажмёт F8 (добавьте nowait, чтобы не ждать)
                say 1 "Привет! Это новый сюжет."
                laugh alice
                wait 2s
                """.formatted(id, title.replace("\"", "'"));
    }

    public static String demo(int id) {
        return """
                script %d "Встреча у костра"

                // Ctrl+Space — подсказки, наведите мышь на команду — справка.
                // Alt+P — вставить ваши текущие координаты.
                // Реплики (say) ждут нажатия F8. Модели и анимации — в проводнике, «Ассеты игры».

                character alice = "spraute:npc" name "Алиса"
                location camp = (0, 64, 0)
                location gate = (8, 64, 4) yaw 90
                var visits = 0

                label begin
                title "Глава 1" sub "Костёр"
                spawn alice at camp
                look alice at player
                speaker 1 alice
                say 1 "Привет, {playername()}!"
                wait near alice radius 4

                choice alice "Пойдёшь со мной к воротам?" emotion HAPPY_NEW {
                    "Да" {
                        say 1 "Тогда за мной!"
                        move alice to gate speed 1
                        wait near gate
                    }
                    "Нет" {
                        set visits += 1
                        say 1 "Жаль... Подумай ещё."
                        wait 3s
                        if visits < 3 {
                            goto begin
                        }
                    }
                }

                think "Интересно, что будет дальше?"
                stop
                """.formatted(id);
    }
}
