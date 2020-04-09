package com.maxwen.osmviewer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    private MainController mController;
    private Scene mScene;

    @Override
    public void start(Stage primaryStage) throws Exception {
        LogUtils.log("start");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("sample.fxml"));
        Parent root = loader.load();

        mController = loader.getController();
        mController.setStage(primaryStage);
        primaryStage.setTitle("OSM");
        mScene = new Scene(root, 1200, 780);
        PerspectiveCamera camera = new PerspectiveCamera();
        mScene.setCamera(camera);
        // to get sizes in setScene
        root.layout();
        mController.setScene(mScene);
        primaryStage.setScene(mScene);
        primaryStage.show();
        primaryStage.getIcons().add(new Image("/images/launcher.png"));

        mController.loadMapData();

        /*Pane pane = new Pane();
        Rectangle r = new Rectangle(100, 100, 200, 50);
        pane.getChildren().add(r);
        Rotate rotate = new Rotate();
        System.err.println(r.getX() + ":" + r.getY());

        rotate.setAngle(45);
        rotate.setPivotY(r.getLayoutBounds().getCenterY());
        rotate.setPivotX(r.getLayoutBounds().getCenterX());
        r.getTransforms().add(rotate);


        Scene scene = new Scene(pane, 400, 400);
        camera = new PerspectiveCamera();
        scene.setCamera(camera);
        Stage sub = new Stage();
        sub.setTitle("Test");
        sub.setScene(scene);
        sub.show();

        Point2D p = new Point2D(r.getX(), r.getY());
        System.err.println(p);
        p = r.getLocalToSceneTransform().transform(p);
        System.err.println(p);

        p = new Point2D(r.getX() + r.getWidth(), r.getY() + r.getHeight());
        System.err.println(p);
        p = r.getLocalToSceneTransform().transform(p);
        System.err.println(p);

        p = new Point2D(0, 0);
        System.err.println(p);
        p = pane.getLocalToSceneTransform().transform(p);
        System.err.println(p);

        System.err.println(r.localToParent(0, 0));*/
    }

    @Override
    public void init() throws Exception {
        super.init();
        LogUtils.log("init");
        DatabaseController.getInstance().connextAll();
    }

    @Override
    public void stop() {
        LogUtils.log("stop");
        mController.stop();
        DatabaseController.getInstance().disconnectAll();
        Config.getInstance().save();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
