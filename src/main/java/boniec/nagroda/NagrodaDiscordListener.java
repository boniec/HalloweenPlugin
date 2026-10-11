package boniec.nagroda;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Odbiera klikniecie przycisku i wypelniony formularz - dalsza logika idzie na glowny watek serwera. */
public class NagrodaDiscordListener extends ListenerAdapter {

    private final JavaPlugin plugin;
    private final NagrodaManager manager;

    public NagrodaDiscordListener(JavaPlugin plugin, NagrodaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!NagrodaManager.BUTTON_ID.equals(event.getComponentId())) return;
        if (event.isAcknowledged()) return;
        event.replyModal(manager.zbudujModal()).queue(null, this::zignorujJuzPotwierdzone);
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!NagrodaManager.MODAL_ID.equals(event.getModalId())) return;
        ModalMapping nickPole = event.getValue("nick");
        ModalMapping kodPole = event.getValue("kod");
        if (nickPole == null || kodPole == null) {
            odpowiedz(event, "❌ Nieprawidłowy formularz.");
            return;
        }
        final String nick = nickPole.getAsString().trim();
        final String kod = kodPole.getAsString().trim();
        final Member member = event.getMember();
        final long discordId = event.getUser().getIdLong();

        if (event.isAcknowledged()) {
            event.getHook().editOriginal("❌ Spróbuj ponownie.").queue(null, this::zignorujJuzPotwierdzone);
            return;
        }

        event.deferReply(true).queue(hook -> Bukkit.getScheduler().runTask(plugin, () -> {
            String odpowiedz;
            try {
                odpowiedz = manager.odbierz(discordId, member, nick, kod);
            } catch (Exception e) {
                plugin.getLogger().warning("[Nagroda] Blad podczas odbierania nagrody: " + e.getMessage());
                odpowiedz = "❌ Wystąpił błąd. Spróbuj ponownie lub napisz do administracji.";
            }
            hook.editOriginal(odpowiedz).queue(null, this::zignorujJuzPotwierdzone);
        }), this::zignorujJuzPotwierdzone);
    }

    private void odpowiedz(ModalInteractionEvent event, String tekst) {
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(tekst).queue(null, this::zignorujJuzPotwierdzone);
            return;
        }
        event.reply(tekst).setEphemeral(true).queue(null, this::zignorujJuzPotwierdzone);
    }

    /** 40060 / 10062: ta sama interakcja poszla do drugiej sesji bota (reload / dwa procesy). */
    private void zignorujJuzPotwierdzone(Throwable t) {
        if (t instanceof ErrorResponseException ere) {
            ErrorResponse kod = ere.getErrorResponse();
            if (kod == ErrorResponse.INTERACTION_ALREADY_ACKNOWLEDGED
                    || kod == ErrorResponse.UNKNOWN_INTERACTION) {
                return;
            }
        }
        plugin.getLogger().warning("[Nagroda] Discord: " + t.getMessage());
    }
}
