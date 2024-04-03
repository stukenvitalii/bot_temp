package edu.java.configuration;

import edu.java.exception.ClientException;
import edu.java.exception.ServerException;
import edu.java.github.GitHubClient;
import edu.java.github.GitHubRepository;
import edu.java.model.dto.Link;
import edu.java.model.request.LinkUpdateRequest;
import edu.java.service.LinkService;
import edu.java.service.sender.SenderService;
import edu.java.stackoverflow.StackOverFlowClient;
import edu.java.stackoverflow.StackOverFlowQuestion;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Component
@EnableScheduling
@EnableRetry
@ConditionalOnProperty(value = "app.scheduler.enable", havingValue = "true", matchIfMissing = true)
public class LinkUpdateScheduler {
    private final Logger logger = Logger.getLogger(LinkUpdateScheduler.class.getName());
    private final LinkService linkService;

    public LinkUpdateScheduler(LinkService linkService) {
        this.linkService = linkService;
    }

    @Autowired
    private GitHubClient gitHubClient;

    @Autowired
    private StackOverFlowClient stackOverFlowClient;

    @Autowired
    private SenderService senderService;

    @Scheduled(fixedDelayString = "#{scheduler.interval}")
    public void update() {
        logger.info("I'm updating!");
        updateOldLinks();
    }

    private void updateOldLinks() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        for (Link link : linkService.getUnUpdatedLinks()) {
            if (link.getUrl().getHost().equals("github.com")) {
                updateGithubLink(link, now);
            } else if (link.getUrl().getHost().equals("stackoverflow.com")) {
                updateStackOverFlowLink(link, now);
            }
            //! this is for testing
            // TODO remove
            LinkUpdateRequest linkUpdateRequest = new LinkUpdateRequest(1L, link.getUrl(), "Обновление данных", List.of(link.getChatId()));
            senderService.updateLink(linkUpdateRequest);
        }
    }

    private void updateStackOverFlowLink(Link link, Timestamp now) {

        String path = link.getUrl().toString();
        Pattern pattern = Pattern.compile("questions/(?<id>\\d+)/");
        Matcher matcher = pattern.matcher(path); //TODO use regex

        try {
            StackOverFlowQuestion question =
                stackOverFlowClient.fetchQuestion(Long.parseLong("5765686574576456454")).getItems().getFirst();
            Timestamp lastActivity = question.getLastActivityAsTimestamp();

            if (lastActivity.after(link.getLastCheckTime())) {
                String description = "обновление данных : ";
                linkService.updateLinkLastCheckTimeById(link.getId(), now);

                if (question.getAnswerCount() > linkService.getLinkPropertiesById(link.getId()).getCountOfAnswer()) {
                    description += "\n"
                        + "появился новый ответ";
                    linkService.updateCountOfAnswersById(link.getId(), question.getAnswerCount());
                }

                if (question.getCommentCount() > linkService.getLinkPropertiesById(link.getId()).getCountOfComments()) {
                    description += "\n"
                        + "появился новый комментарий";
                    linkService.updateCountOfCommentsById(link.getId(), question.getCommentCount());
                }
                LinkUpdateRequest linkUpdateRequest = new LinkUpdateRequest(1L, link.getUrl(), description, List.of(link.getChatId()));
                senderService.updateLink(linkUpdateRequest);
            }
        } catch (ClientException | ServerException e) {
            logger.error(e.getMessage());
        }
    }

    private void updateGithubLink(Link link, Timestamp now) {
        String url = link.getUrl().toString();
        String owner = extractOwnerName(url);
        String repoName = extractRepoName(url);

        try {
            GitHubRepository rep = gitHubClient.getRepositoryInfo(owner, repoName).block();
            Timestamp lastPush = rep.getLastPush();

            if (lastPush.after(link.getLastCheckTime())) {
                LinkUpdateRequest linkUpdateRequest = new LinkUpdateRequest(1L, link.getUrl(), "Обновление данных", List.of(link.getChatId()));
                senderService.updateLink(linkUpdateRequest);
                linkService.updateLinkLastCheckTimeById(link.getId(), now);
            }
        } catch (ServerException | ClientException | WebClientRequestException ex) {
            logger.error(ex.getMessage());
        }
    }

    public static String extractOwnerName(String githubUrl) {
        Pattern pattern = Pattern.compile("github.com/(?<owner>[^/]+)/");
        Matcher matcher = pattern.matcher(githubUrl);

        if (matcher.find()) {
            return matcher.group("owner");
        } else {
            return null;
        }
    }

    public static String extractRepoName(String githubUrl) {
        Pattern pattern = Pattern.compile("/(?<repo>[^/]+)$");
        Matcher matcher = pattern.matcher(githubUrl);

        if (matcher.find()) {
            return matcher.group("repo");
        } else {
            return null;
        }
    }
}
