<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<BorderPane xmlns="http://javafx.com/javafx/17" xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.app.controller.MainShellController"
            prefWidth="800.0" prefHeight="600.0">
            
    <!-- Navigation / Sidebar bên trái -->
    <left>
        <VBox spacing="10" style="-fx-background-color: #2c3e50; -fx-padding: 15;">
            <Label text="UI States Demo" style="-fx-text-fill: white; -fx-font-weight: bold;"/>
            <Button text="1. Loading State" onAction="#onShowLoading" maxWidth="INFINITY"/>
            <Button text="2. Empty State" onAction="#onShowEmpty" maxWidth="INFINITY"/>
            <Button text="3. Error State" onAction="#onShowError" maxWidth="INFINITY"/>
            <Button text="4. Reconnecting State" onAction="#onShowReconnecting" maxWidth="INFINITY"/>
            <Button text="5. Closed State" onAction="#onShowClosed" maxWidth="INFINITY"/>
        </VBox>
    </left>

    <!-- Khu vực hiển thị nội dung chính / UI State -->
    <center>
        <StackPane fx:id="mainContentArea" style="-fx-background-color: #f5f6fa;"/>
    </center>
    
</BorderPane>