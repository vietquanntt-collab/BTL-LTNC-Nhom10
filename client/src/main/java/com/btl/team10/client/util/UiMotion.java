package com.btl.team10.client.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.concurrent.ThreadLocalRandom;

public final class UiMotion {

    private static final String TYPEWRITER_KEY = "ui.motion.typewriter";

    private UiMotion() {
    }

    public static void animateViewEntrance(Node node) {
        animateEntrance(node);
    }

    public static void animateEntrance(Node node) {
        if (node == null) {
            return;
        }
        node.setOpacity(0.0);
        node.setTranslateY(12.0);

        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), node);
        slide.setFromY(12.0);
        slide.setToY(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide).play();
    }

    public static void shake(Node node) {
        if (node == null) {
            return;
        }
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(45), new KeyValue(node.translateXProperty(), -7)),
                new KeyFrame(Duration.millis(90), new KeyValue(node.translateXProperty(), 7)),
                new KeyFrame(Duration.millis(135), new KeyValue(node.translateXProperty(), -5)),
                new KeyFrame(Duration.millis(180), new KeyValue(node.translateXProperty(), 5)),
                new KeyFrame(Duration.millis(225), new KeyValue(node.translateXProperty(), -2)),
                new KeyFrame(Duration.millis(260), new KeyValue(node.translateXProperty(), 0))
        );
        timeline.play();
    }

    public static void pulse(Node node) {
        pulse(node, 1.08);
    }

    public static void pulse(Node node, double peakScale) {
        if (node == null) {
            return;
        }
        ScaleTransition pulse = new ScaleTransition(Duration.millis(160), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(peakScale);
        pulse.setToY(peakScale);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();
    }

    public static void startLiveBlink(Node dot) {
        if (dot == null) {
            return;
        }
        FadeTransition blink = new FadeTransition(Duration.millis(850), dot);
        blink.setFromValue(1.0);
        blink.setToValue(0.22);
        blink.setAutoReverse(true);
        blink.setCycleCount(Animation.INDEFINITE);
        blink.play();
    }

    public static void attachHoverLift(Node card) {
        if (card == null) {
            return;
        }
        card.setOnMouseEntered(e -> {
            TranslateTransition lift = new TranslateTransition(Duration.millis(140), card);
            lift.setToY(-4.0);
            ScaleTransition scale = new ScaleTransition(Duration.millis(140), card);
            scale.setToX(1.015);
            scale.setToY(1.015);
            new ParallelTransition(lift, scale).play();
        });
        card.setOnMouseExited(e -> {
            TranslateTransition drop = new TranslateTransition(Duration.millis(140), card);
            drop.setToY(0.0);
            ScaleTransition scale = new ScaleTransition(Duration.millis(140), card);
            scale.setToX(1.0);
            scale.setToY(1.0);
            new ParallelTransition(drop, scale).play();
        });
    }

    public static Timeline animatePriceCounter(
            Label label, long fromValue, long toValue, String suffix, Timeline existingTimeline) {
        if (existingTimeline != null) {
            existingTimeline.stop();
        }
        String safeSuffix = suffix != null ? suffix : "";
        if (label == null) {
            return null;
        }
        if (fromValue == toValue) {
            label.setText(String.format(java.util.Locale.US, "%,d", toValue) + safeSuffix);
            return null;
        }

        int steps = 12;
        Timeline timeline = new Timeline();
        for (int i = 1; i <= steps; i++) {
            double progress = (double) i / steps;
            long current = Math.round(fromValue + (toValue - fromValue) * progress);
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.millis(26L * i), e -> label.setText(String.format(java.util.Locale.US, "%,d", current) + safeSuffix))
            );
        }
        timeline.setOnFinished(e -> label.setText(String.format(java.util.Locale.US, "%,d", toValue) + safeSuffix));
        pulse(label, 1.10);
        timeline.play();
        return timeline;
    }

    public static Timeline animatePriceCounter(Label label, long fromValue, long toValue, String suffix) {
        return animatePriceCounter(label, fromValue, toValue, suffix, null);
    }

    public static Timeline typewriter(Label label, String fullText) {
        return typewriter(label, fullText, null, 14.0);
    }

    public static Timeline typewriter(Label label, String fullText, Timeline existingTimeline) {
        return typewriter(label, fullText, existingTimeline, 14.0);
    }

    public static Timeline typewriter(Label label, String fullText, double charDelayMillis) {
        return typewriter(label, fullText, null, charDelayMillis);
    }

    private static Timeline typewriter(
            Label label, String fullText, Timeline existingTimeline, double charDelayMillis) {
        if (existingTimeline != null) {
            existingTimeline.stop();
        }
        if (label == null) {
            return null;
        }
        Object stored = label.getProperties().get(TYPEWRITER_KEY);
        if (stored instanceof Timeline oldTimeline) {
            oldTimeline.stop();
        }
        if (fullText == null || fullText.isEmpty()) {
            label.setText("");
            return null;
        }

        label.setText("");
        int length = fullText.length();
        double stepMs = Math.max(5.0, charDelayMillis);
        Timeline timeline = new Timeline();
        for (int i = 1; i <= length; i++) {
            int endIdx = i;
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.millis(stepMs * i), e -> label.setText(fullText.substring(0, endIdx)))
            );
        }
        label.getProperties().put(TYPEWRITER_KEY, timeline);
        timeline.play();
        return timeline;
    }

    public static void burstConfetti(Pane layer) {
        launchConfetti(layer, 32);
    }

    public static void burstConfetti(Pane layer, int count) {
        launchConfetti(layer, count);
    }

    public static void launchConfetti(Pane layer) {
        launchConfetti(layer, 32);
    }

    public static void launchConfetti(Pane layer, int count) {
        if (layer == null) {
            return;
        }
        double width = layer.getWidth() > 0 ? layer.getWidth() : 760;
        double height = layer.getHeight() > 0 ? layer.getHeight() : 275;
        Color[] palette = {
                Color.web("#f1c40f"),
                Color.web("#fde047"),
                Color.web("#38bdf8"),
                Color.web("#4ade80"),
                Color.web("#fb7185")
        };

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int pieces = Math.max(1, count);
        for (int i = 0; i < pieces; i++) {
            Rectangle piece = new Rectangle(rnd.nextDouble(6, 11), rnd.nextDouble(4, 8));
            piece.setArcWidth(3);
            piece.setArcHeight(3);
            piece.setFill(palette[i % palette.length]);
            piece.setLayoutX(rnd.nextDouble(20, Math.max(40, width - 20)));
            piece.setLayoutY(-12);
            layer.getChildren().add(piece);

            Duration fallTime = Duration.millis(rnd.nextDouble(1300, 2200));
            TranslateTransition fall = new TranslateTransition(fallTime, piece);
            fall.setByX(rnd.nextDouble(-45, 45));
            fall.setByY(height + 30);

            RotateTransition spin = new RotateTransition(fallTime, piece);
            spin.setByAngle(rnd.nextDouble(240, 640));

            FadeTransition fade = new FadeTransition(fallTime, piece);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);

            ParallelTransition anim = new ParallelTransition(fall, spin, fade);
            anim.setDelay(Duration.millis(rnd.nextDouble(0, 350)));
            anim.setOnFinished(e -> layer.getChildren().remove(piece));
            anim.play();
        }
    }
}
