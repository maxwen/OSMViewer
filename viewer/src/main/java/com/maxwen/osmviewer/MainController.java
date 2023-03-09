package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.model.QueryItem;
import com.maxwen.osmviewer.model.RouteStep;
import com.maxwen.osmviewer.model.RoutingNode;
import com.maxwen.osmviewer.nmea.NMEAHandler;
import com.maxwen.osmviewer.model.Route;
import com.maxwen.osmviewer.shared.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Pair;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainController implements Initializable, NMEAHandler {
    public static final int ROTATE_X_VALUE = 60;
    public static final int PREFETCH_MARGIN_PIXEL = 800;
    @FXML
    Button quitButton;
    @FXML
    Button searchButton;
    @FXML
    Button zoomInButton;
    @FXML
    Button zoomOutButton;
    @FXML
    Label zoomLabel;
    @FXML
    Label zoomTitle;
    @FXML
    Label posLabel;
    @FXML
    Label posTitle;
    @FXML
    BorderPane borderPane;
    @FXML
    ToggleButton trackModeButton;
    @FXML
    Label speedLabel;
    @FXML
    Label altLabel;
    @FXML
    Button startReplayButton;
    @FXML
    Button stopReplayButton;
    @FXML
    Button pauseReplayButton;
    @FXML
    HBox trackButtons;
    @FXML
    Button stepReplayButton;
    @FXML
    HBox bottomPane;
    @FXML
    HBox topPane;
    @FXML
    Button menuButton;
    @FXML
    Label wayLabel;
    @FXML
    HBox rightPane;
    @FXML
    VBox rightButtons;
    @FXML
    HBox infoBox;
    @FXML
    Label infoLabel;
    @FXML
    Label infoTitle;
    @FXML
    Pane infoPane;
    VBox searchContent;
    @FXML
    StackPane mainStackPane;
    @FXML
    StackPane rootPane;

    Pane mainPane;
    VBox filterContent;
    VBox sidePane;
    VBox savedNodeListContent;
    VBox routeNodeListContent;

    private static final int MIN_ZOOM = 4;
    private static final int MAX_ZOOM = 20;
    private int mMapZoom = 17;
    private Point2D mMapZero = new Point2D(0, 0);
    private Point2D mCenter = new Point2D(12.992203, 47.793938);
    private Point2D mCenterPos = new Point2D(0, 0);
    private Point2D mMovePoint;
    private Stage mPrimaryStage;
    private boolean mShow3D;
    private Scene mScene;
    private BoundingBox mFetchBBox;
    private BoundingBox mVisibleBBox;
    private Rotate mRotate;
    private Map<Integer, List<Node>> mWayPolylines;
    private List<OSMImageView> mNodes;
    private Rotate mZRotate;
    private OSMShape mSelectdShape;
    private long mSelectdOSMId = -1;
    private Map<Long, JsonObject> mOSMObjects;
    private Point2D mGPSPos = new Point2D(0, 0);
    private Circle mGPSDot;
    private JsonObject mGPSData;
    private boolean mTrackMode;
    private GPSThread mGPSThread;
    private boolean mTrackReplayMode;
    private Set<Long> mPredictionWays = new HashSet<>();
    private ContextMenu mMenuButtonMenu;
    private ContextMenu mContextMenu;
    private Point2D mMapGPSPos;
    private TrackReplayThread mTrackReplayThread;
    private JsonObject mCurrentEdge;
    private JsonArray mNextEdgeList = new JsonArray();
    private long mNextRefId = -1;
    private File mCurrentTrackFile;
    private JsonObject mLastUsedEdge;
    private OSMShape mTrackingShape;
    private long mTrackingOSMId = -1;
    private Pane mMapPane = new Pane();
    private Pane mNodePane = new Pane();
    private Pane mRoutingPane = new Pane();
    private Pane mRoutingNodePane = new Pane();

    private Pane mTrackingPane = new Pane();
    private Pane mCountryPane = new Pane();
    private Pane mAreaPane = new Pane();
    private Pane mTilePane = new Pane();
    private Pane mLabelPane = new Pane();

    private List<Pane> mTransformPanes = new ArrayList<>();
    private ExecutorService mExecutorService;
    private LoadPOITask mLoadPOITask;
    private LoadLabelTask mLoadLabelTask;
    private LoadAreaTask mLoadAreaTask;
    private Map<Integer, List<Node>> mAreaPolylines;
    private Map<Integer, List<Node>> mCountryPolylines;
    private JsonObject mSelectedEdge;
    private Polyline mSelectedEdgeShape;
    private ResolvePositionTask mResolvePositionTask;
    private List<RoutingNode> mRoutingNodes = new ArrayList<>();
    private List<RoutingNode> mSavedNodes = new ArrayList<>();
    private List<Route> mRouteList = new ArrayList<>();

    private Point2D mMouseClickedNodePos;
    private Point2D mMouseClickedCoordsPos;

    private List<Polyline> mRoutePolylineList = new ArrayList<>();
    private List<Route> mRouteEdgeIdList = new ArrayList<>();
    private boolean mMapLoading;
    private LoadMapDataTask mLoadMapDataTask;
    private boolean mSidePaneExpanded;
    private ObservableList<QueryItem> mQueryItems = FXCollections.observableArrayList();
    private ObservableList<RoutingNode> mRouteNodesListItems = FXCollections.observableArrayList();
    private ObservableList<RoutingNode> mSavedNodesListItems = FXCollections.observableArrayList();
    private ObservableList<RouteStep> mRouteAListItems = FXCollections.observableArrayList();
    private ObservableList<RouteStep> mRouteBListItems = FXCollections.observableArrayList();

    private ProgressIndicator mQueryListProgress;
    private ProgressIndicator mProgress;
    private Button mProgressStop;
    private MenuButton mFilterHeader;
    private Rectangle mOutlineRect;
    private JsonObject mTileStyle;
    private JsonArray mAdminIdList;
    private VBox mRouteAContent;
    private VBox mRouteBContent;
    private Text mRouteALength;
    private Text mRouteATime;
    private Text mRouteBLength;
    private Text mRouteBTime;

    public boolean isTransparentWays() {
        return mTransparentWays;
    }

    enum FilterType {
        POI,
        ADDRESS,
        CITY,
        LOCAL
    }

    private FilterType mFilterType = FilterType.POI;
    private JsonArray mLocationHistory = new JsonArray();
    private int mLocationHistoryIndex;
    private List<OSMTextLabel> mLabelShapeList = new ArrayList<>();
    private QueryMapTilesTask mQueryTilesTask;
    private String mTilesHome;
    private boolean mTransparentWays;
    private JsonObject mFilterConfig;
    private Circle mCalcPoint;
    private ListView<QueryItem> mQueryListView;
    private TextField mQueryField;
    private QueryTaskPOI mQueryTask;
    private String mQueryText;
    private ListView<RoutingNode> mSavedNodesListView;
    private ListView<RoutingNode> mRouteNodesListView;
    private ListView<RouteStep> mRouteAListView;
    private ListView<RouteStep> mRouteBListView;

    private static final int HTTP_READ_TIMEOUT = 30000;
    private static final int HTTP_CONNECTION_TIMEOUT = 30000;

    public static final int TUNNEL_LAYER_LEVEL = -1;
    public static final int ADMIN_AREA_LAYER_LEVEL = 0;
    public static final int AREA_LAYER_LEVEL = 1;
    public static final int BUILDING_AREA_LAYER_LEVEL = 2;
    public static final int HIDDEN_STREET_LAYER_LEVEL = 3;
    public static final int STREET_LAYER_LEVEL = 4;
    public static final int RAILWAY_LAYER_LEVEL = 5;
    public static final int BRIDGE_LAYER_LEVEL = 6;

    EventHandler<MouseEvent> mouseHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent mouseEvent) {
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED) {
                if (!mouseEvent.isStillSincePress()) {
                    return;
                }
                //LogUtils.log("MOUSE_CLICKED:mouseHandler");

                Point2D mouseScreenPos = new Point2D(mouseEvent.getX(), mouseEvent.getY());
                Point2D paneZeroPos = mNodePane.localToScreen(0, 0);
                Point2D nodePos = new Point2D(mouseEvent.getScreenX() - paneZeroPos.getX() + mMapZero.getX(),
                        mouseEvent.getScreenY() - paneZeroPos.getY() + mMapZero.getY());

                mMouseClickedNodePos = nodePos;
                mMouseClickedCoordsPos = getCoordOfPos(mouseScreenPos);
                mMovePoint = null;

                OSMShape clickedShape = null;
                hideAllMenues();

                if (mouseEvent.getButton() == MouseButton.SECONDARY) {
                    if (!mMapLoading) {
                        buildContextMenu();
                        mContextMenu.show(mainPane, mouseEvent.getScreenX(), mouseEvent.getScreenY());
                    }
                    return;
                }

                LogUtils.log("admin = " + getAdminAreaStringAtPos(mMouseClickedCoordsPos) + " " + getAdminIdAtPos(mMouseClickedCoordsPos));
                if (!mTrackReplayMode && !mTrackMode) {
                    posLabel.setText(String.format("%.5f:%.5f", mMouseClickedCoordsPos.getX(), mMouseClickedCoordsPos.getY()));
                }
                if (mMapZoom >= 14) {
                    // first check for poi nodes with screen pos
                    for (OSMImageView node : mNodes) {
                        if (node.contains(nodePos)) {
                            clickedShape = node;
                            break;
                        }
                    }
                    if (clickedShape == null) {
                        Point2D mapPosNormalized = new Point2D(mouseScreenPos.getX() + mMapZero.getX(), mouseScreenPos.getY() + mMapZero.getY());
                        clickedShape = findShapeAtPoint(mapPosNormalized, OSMUtils.SELECT_AREA_TYPE);
                    }
                }

                if (clickedShape != null) {
                    MainController.this.doSelectShape(clickedShape);
                    if (mSelectdShape instanceof OSMPolyline) {
                        JsonObject osmObject = mOSMObjects.get(mSelectdOSMId);
                        if (osmObject != null && osmObject.get("type").equals("way")) {
                            if (mResolvePositionTask != null) {
                                mResolvePositionTask.cancel();
                            }
                            mResolvePositionTask = new ResolvePositionTask(mMouseClickedCoordsPos, mSelectdOSMId, -1);
                            mResolvePositionTask.setOnSucceeded((succeededEvent) -> {
                                mSelectedEdge = mResolvePositionTask.getResolvedEdge();
                                if (mSelectedEdge != null) {
                                    createSelectedEdgeShape();
                                    drawShapes();
                                }
                            });
                            mExecutorService.submit(mResolvePositionTask);
                        }
                    }
                }
            }
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                //LogUtils.log("MOUSE_DRAGGED:mouseHandler");
                hideAllMenues();

                if (mMovePoint == null) {
                    mMovePoint = new Point2D(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                }
                int diffX = (int) (mMovePoint.getX() - mouseEvent.getSceneX());
                int diffY = (int) (mMovePoint.getY() - mouseEvent.getSceneY());
                //if (Math.abs(diffX) > 5 || Math.abs(diffY) > 5) {
                moveMap(diffX, diffY);
                mMovePoint = new Point2D(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                //}
            }
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_RELEASED) {
                //LogUtils.log("MOUSE_RELEASED:mouseHandler");
                mMovePoint = null;
            }
            mouseEvent.consume();
        }
    };

    public class LoadPOITask extends LoadMapTask<Void> {

        public LoadPOITask(List<Double> bbox) {
            super(bbox, 17);
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("LoadPOITask " + bbox);

            mNodes.clear();

            if (isCancelled()) {
                LogUtils.log("LoadPOITask cancel");
                return null;
            }

            if (mMapZoom >= 17) {
                JsonArray nodes = QueryController.getInstance().getPOINodesInBBoxWithGeom(bbox.get(0), bbox.get(1),
                        bbox.get(2), bbox.get(3), getPoiTypeListForZoom(), MainController.this);
                for (int i = 0; i < nodes.size(); i++) {
                    JsonObject node = (JsonObject) nodes.get(i);
                    long osmId = (long) node.get("osmId");
                    int nodeType = (int) node.get("nodeType");
                    JsonArray coord = (JsonArray) node.get("coords");

                    Point2D nodePos = coordinateToDisplay(coord.getDouble(0), coord.getDouble(1), mMapZoom);
                    Image poiImage = OSMStyle.getNodeTypeImage(nodeType);
                    if (poiImage == null) {
                        poiImage = OSMStyle.getDefaultNodeImage();
                    }
                    OSMImageView poi = new OSMImageView(nodePos, poiImage, osmId);
                    updatePOINode(poi, true);
                    mNodes.add(poi);

                    if (isCancelled()) {
                        break;
                    }
                }
            }
            return null;
        }

        @Override
        public EventHandler<WorkerStateEvent> getSucceedEvent() {
            return new EventHandler<WorkerStateEvent>() {
                @Override
                public void handle(WorkerStateEvent event) {
                    LogUtils.log("loadMapData: LoadPOITask succeededEvent");
                    drawShapes();
                }
            };
        }

        @Override
        public EventHandler<WorkerStateEvent> getCancelEvent() {
            return null;
        }

        @Override
        public EventHandler<WorkerStateEvent> getRunningEvent() {
            return null;
        }
    }

    public class LoadLabelTask extends LoadMapTask<Void> {

        public LoadLabelTask(List<Double> bbox) {
            super(bbox, 4);
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("LoadLabelTask " + bbox);

            mLabelShapeList.clear();

            if (isCancelled()) {
                LogUtils.log("LoadLabelTask cancel");
                return null;
            }

            if (mMapZoom >= 12) {
                JsonArray nodes = QueryController.getInstance().getPOINodesInBBoxWithGeom(bbox.get(0), bbox.get(1),
                        bbox.get(2), bbox.get(3), List.of(OSMUtils.POI_TYPE_PLACE), MainController.this);
                for (int i = 0; i < nodes.size(); i++) {
                    JsonObject node = (JsonObject) nodes.get(i);
                    String name = (String) node.get("name");
                    if (name != null) {
                        long osmId = (long) node.get("osmId");
                        JsonArray coord = (JsonArray) node.get("coords");
                        JsonObject tags = (JsonObject) node.get("tags");
                        String placeType = (String) tags.get("place");
                        LogUtils.log("place type = " + placeType);
                        Point2D nodePos = coordinateToDisplay((double) coord.get(0), (double) coord.get(1), mMapZoom);
                        OSMTextLabel label = new OSMTextLabel(nodePos, name, osmId);
                        label.setStyle(String.format("-fx-font-size: %d", getPlaceLabelFontSizeForZoom(placeType)));
                        updateLabelShape(label);
                        mLabelShapeList.add(label);
                    }

                    if (isCancelled()) {
                        break;
                    }
                }
            } else {
                JsonArray countries = QueryController.getInstance().getAdminAreasWithCenter("(2)", MainController.this);
                for (int i = 0; i < countries.size(); i++) {
                    JsonObject country = (JsonObject) countries.get(i);
                    long osmId = (long) country.get("osmId");
                    String name = (String) country.get("name");
                    name = name.replace("/", "/\n");
                    JsonArray coord = (JsonArray) country.get("center");
                    Point2D nodePos = coordinateToDisplay((double) coord.get(0), (double) coord.get(1), mMapZoom);
                    OSMTextLabel label = new OSMTextLabel(nodePos, name, osmId);
                    label.setStyle(String.format("-fx-font-size: %d", getCountryLabelFontSizeForZoom()));
                    updateLabelShape(label);
                    mLabelShapeList.add(label);
                }
            }
            return null;
        }

        @Override
        public EventHandler<WorkerStateEvent> getSucceedEvent() {
            return new EventHandler<WorkerStateEvent>() {
                @Override
                public void handle(WorkerStateEvent event) {
                    LogUtils.log("loadMapData: LoadLabelTask succeededEvent");
                    drawShapes();
                }
            };
        }

        @Override
        public EventHandler<WorkerStateEvent> getCancelEvent() {
            return null;
        }

        @Override
        public EventHandler<WorkerStateEvent> getRunningEvent() {
            return null;
        }
    }

    public class LoadAreaTask extends LoadMapTask<Void> {
        public LoadAreaTask(List<Double> bbox) {
            super(bbox, 14);
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("LoadAreaTask " + bbox);

            if (isCancelled()) {
                LogUtils.log("LoadAreaTask cancel");
                return null;
            }
            if (mMapZoom >= 14) {
                long t = System.currentTimeMillis();
                QueryController.getInstance().getAreasInBboxWithGeom(bbox.get(0), bbox.get(1),
                        bbox.get(2), bbox.get(3), getAreaTypeListForZoom(mTransparentWays), mTransparentWays ? 0 : getSimplifyToleranceForZoom(),
                        mTransparentWays ? 0 : getAreaMinSizeForZoom(), mAreaPolylines, MainController.this, this);

                QueryController.getInstance().getLinesInBboxWithGeom(bbox.get(0), bbox.get(1),
                        bbox.get(2), bbox.get(3), getAreaTypeListForZoom(mTransparentWays), getSimplifyToleranceForZoom(), mAreaPolylines, MainController.this, this);
                LogUtils.log("doLoadMapData LoadAreaTask time = " + (System.currentTimeMillis() - t));
            }
            return null;
        }

        @Override
        public EventHandler<WorkerStateEvent> getSucceedEvent() {
            return event -> {
                LogUtils.log("loadMapData: LoadAreaTask succeededEvent");
                mAreaPane.getChildren().clear();

                for (List<Node> polyList : mAreaPolylines.values()) {
                    mAreaPane.getChildren().addAll(polyList);
                }
                drawShapes();
            };
        }

        @Override
        public EventHandler<WorkerStateEvent> getCancelEvent() {
            return event -> LogUtils.log("loadMapData: LoadAreaTask cancelEvent");
        }

        @Override
        public EventHandler<WorkerStateEvent> getRunningEvent() {
            return event -> LogUtils.log("loadMapData: LoadAreaTask runningEvent");
        }
    }

    public class ResolvePositionTask extends Task<Void> {
        private Point2D mCoordsPos;
        private JsonObject mEdge;
        private long mWayId;
        private JsonObject mWay;
        private JsonObject mArea;
        private long mOsmId = -1;

        public ResolvePositionTask(Point2D coordsPos, long wayId, long osmId) {
            mCoordsPos = coordsPos;
            mWayId = wayId;
            mOsmId = osmId;
        }

        public JsonObject getResolvedEdge() {
            return mEdge;
        }

        public JsonObject getResolvedWay() {
            return mWay;
        }

        public JsonObject getResolvedArea() {
            return mArea;
        }

        @Override
        protected Void call() throws Exception {
            JsonArray edgeList = QueryController.getInstance().getEdgeOnPos(mCoordsPos.getX(), mCoordsPos.getY(), 0.0005, 30, 20);
            if (edgeList.size() != 0) {
                if (edgeList.size() == 1) {
                    mEdge = (JsonObject) edgeList.get(0);
                } else {
                    for (int i = 0; i < edgeList.size(); i++) {
                        JsonObject edge = (JsonObject) edgeList.get(i);
                        if ((long) edge.get("wayId") == mWayId) {
                            mEdge = edge;
                            break;
                        }
                    }
                }
                if (mEdge != null) {
                    if ((long) mEdge.get("wayId") == mWayId) {
                        LogUtils.log("mEdge = " + mEdge);
                        mWay = QueryController.getInstance().getWayEntryForId(mWayId);
                    } else {
                        LogUtils.error("ResolvePositionTask: failed to resolve edge for position");
                        mEdge = null;
                        mWay = null;
                    }
                }
                if (mOsmId != -1) {
                    mArea = QueryController.getInstance().getAddressEntryForId(mOsmId);
                }
            }
            return null;
        }
    }

    public class CalcRouteTaskExternal extends Task<Void> {
        private long mStartEdgeId;
        private long mEndEdgeId;
        private List<Route> mRouteList = new ArrayList<>();
        private Process mProcess;

        public CalcRouteTaskExternal(long startEdgeId, long endEdgeId) {
            mStartEdgeId = startEdgeId;
            mEndEdgeId = endEdgeId;
        }

        public void destroy() {
            if (mProcess != null) {
                LogUtils.log("CalcRouteTaskExternal: destroy");

                mProcess.destroy();
                mProcess = null;
            }
            cancel();
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("CalcRouteTaskExternal: mStartEdgeId = " + mStartEdgeId + " mEndEdgeId = " + mEndEdgeId);

            String calcRoutePath = System.getProperty("osm.calc_route.path");
            calcRoutePath = System.getenv().getOrDefault("OSM_CALC_ROUTE_PATH", calcRoutePath);

            ProcessBuilder pb = new ProcessBuilder("/bin/sh", calcRoutePath + "/routing/bin/routing", String.valueOf(mStartEdgeId), String.valueOf(mEndEdgeId));
            pb.environment().put("ROUTING_OPTS", String.format("-Djava.library.path=%s/routing/lib", calcRoutePath));
            pb.inheritIO();
            mProcess = pb.start();
            int exitCode = mProcess.waitFor();
            LogUtils.log("CalcRouteTaskExternal: exitCode = " + exitCode);

            if (mProcess != null) {
                mProcess = null;

                for (RouteUtils.TYPE type : RouteUtils.routeTypes) {
                    JsonArray routeEdgeIdList = QueryController.getInstance().getEdgeIdListForCalcRoute(mStartEdgeId, mEndEdgeId, type);
                    if (routeEdgeIdList != null) {
                        if (GISUtils.getLongValue(routeEdgeIdList.get(0)) != mStartEdgeId) {
                            routeEdgeIdList.add(0, mStartEdgeId);
                        }
                        if (GISUtils.getLongValue(routeEdgeIdList.get(routeEdgeIdList.size() - 1)) != mEndEdgeId) {
                            routeEdgeIdList.add(mEndEdgeId);
                        }
                        Route route = new Route(getRoutingStart(), getRoutingEnd(), type);
                        route.setEdgeList(routeEdgeIdList);
                        mRouteList.add(route);
                    }
                }
            }

            return null;
        }
    }

    public class LoadMapDataTask extends LoadMapTask<Void> {
        private Runnable mDoAfter;

        public LoadMapDataTask(List<Double> bbox, Runnable doAfter) {
            super(bbox, 0);
            mDoAfter = doAfter;
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("LoadMapDataTask " + mMapZoom);
            mMapLoading = true;
            long t = System.currentTimeMillis();
            doLoadMapData(bbox);
            LogUtils.log("loadMapDataTask time = " + (System.currentTimeMillis() - t));
            return null;
        }

        @Override
        public EventHandler<WorkerStateEvent> getSucceedEvent() {
            return event -> {
                LogUtils.log("loadMapDataTask: LoadMapDataTask succeededEvent");
                doUpdateMapData(bbox, mDoAfter);
                stopProgress();
                mMapLoading = false;
            };
        }

        @Override
        public EventHandler<WorkerStateEvent> getCancelEvent() {
            return event -> stopProgress();
        }

        @Override
        public EventHandler<WorkerStateEvent> getRunningEvent() {
            return event -> startProgress(false);
        }
    }

    public class QueryTaskPOI extends Task<List<QueryItem>> implements QueryTaskCallback {
        private String mQueryString;
        private FilterType mFilterType;
        private List<QueryItem> mQueryItems = new ArrayList<>();
        private JsonArray mAdminIdList;

        public QueryTaskPOI(String queryString, FilterType filterType, JsonArray adminIdList) {
            mQueryString = queryString;
            mFilterType = filterType;
            mAdminIdList = adminIdList;
        }

        @Override
        protected List<QueryItem> call() {
            if (mFilterType == FilterType.POI) {
                QueryController.getInstance().queryPOINodesMatchingName(mQueryString, null, this);
            } else if (mFilterType == FilterType.ADDRESS) {
                QueryController.getInstance().queryAddressMatchingName(mQueryString, this);
            } else if (mFilterType == FilterType.CITY) {
                QueryController.getInstance().queryCityMatchingName(mQueryString, this);
            } else if (mFilterType == FilterType.LOCAL) {
                QueryController.getInstance().queryPOINodesMatchingNameAndAdminId(mQueryString, mAdminIdList, null, this);
                QueryController.getInstance().queryAddressMatchingNameAndAdminId(mQueryString, mAdminIdList, this);
            }
            return mQueryItems;
        }

        @Override
        public boolean addQueryItemPOI(JsonObject node) {
            String type = (String) node.get("type");
            StringBuffer nodeArea = new StringBuffer();

            JsonArray coords = (JsonArray) node.get("coords");
            Point2D coordsPos = new Point2D((double) coords.get(0), (double) coords.get(1));
            JsonObject adminData = (JsonObject) node.get("adminData");
            if (node.containsKey("adminId")) {
                long adminId = (long) node.get("adminId");
                adminData = QueryController.getInstance().getAdminAreasWithId(adminId);
            }
            if (adminData != null) {
                JsonObject tags = (JsonObject) adminData.get("tags");
                if (tags != null && tags.containsKey("name")) {
                    if (!type.equals("city")) {
                        // skip city itsself
                        nodeArea.append(tags.get("name"));
                        nodeArea.append("/");
                    }

                    JsonArray parents = (JsonArray) tags.get("parents");
                    if (parents != null) {
                        for (int j = 0; j < parents.size(); j++) {
                            JsonObject parentAdminArea = QueryController.getInstance().getAdminAreasWithId(GISUtils.getLongValue(parents.get(j)));
                            if (parentAdminArea != null) {
                                JsonObject parentTags = (JsonObject) parentAdminArea.get("tags");
                                if (parentTags != null && parentTags.containsKey("name")) {
                                    nodeArea.append(parentTags.get("name"));
                                    if (j < parents.size() - 1) {
                                        nodeArea.append("/");
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                String adminAreaString = getAdminAreaStringAtPos(new Point2D((double) coords.get(0), (double) coords.get(1)));
                nodeArea.append(adminAreaString);
            }

            if (type.equals("poi")) {
                int poiType = (int) node.get("nodeType");
                QueryItem item = new QueryItem((String) node.get("name"), nodeArea.toString(),
                        OSMStyle.getNodeTypeImage(poiType), coordsPos);
                mQueryItems.add(item);
            } else if (type.equals("address")) {
                QueryItem item = new QueryItem((String) node.get("name"), nodeArea.toString(),
                        null, coordsPos);
                mQueryItems.add(item);
            } else if (type.equals("city")) {
                QueryItem item = new QueryItem((String) node.get("name"), nodeArea.toString(),
                        null, coordsPos);
                mQueryItems.add(item);
            }
            return !isCancelled();
        }
    }

    public class QueryMapTilesTask extends LoadMapTask<List<ImageView>> {
        private final int mXTileNum;
        private final int mYTileNum;
        private List<ImageView> mTilesList = new ArrayList<>();
        // basic or osm
        private String TILE_STYLE = "streets";
        private String mFormat;
        private String mUrl;
        private ExecutorService mDownloadExecutorService;

        public QueryMapTilesTask() {
            super(null, 0);
            try {
                mFormat = (String) ((JsonObject) mTileStyle.get(TILE_STYLE)).get("format");
                mUrl = (String) ((JsonObject) mTileStyle.get(TILE_STYLE)).get("url");
            } catch (Exception e) {
                mFormat = "jpg";
                mUrl = "https://api.maptiler.com/maps/openstreetmap/";
            }
            mDownloadExecutorService = Executors.newFixedThreadPool(8);
            mXTileNum = (int) (mOutlineRect.getWidth() / GISUtils.TILESIZE) + 1;
            mYTileNum = (int) (mOutlineRect.getHeight() / GISUtils.TILESIZE) + (mShow3D ? 3 : 1);
        }

        @Override
        protected List<ImageView> call() throws Exception {
            int zoom = mMapZoom;

            Map<String, File> mDownloadTileQueue = new HashMap<>();
            Pair tileCoords = getTileNumber(mCenter.getY(), mCenter.getX(), zoom);
            int xTile = (int) tileCoords.getKey();
            int yTile = (int) tileCoords.getValue();


            for (int i = yTile - mYTileNum / 2; i <= yTile + mYTileNum / 2; i++) {
                for (int j = xTile - mXTileNum / 2; j <= xTile + mXTileNum / 2; j++) {
                    String relTilePath = zoom + "/" + j + "/" + i + "." + mFormat;

                    File tileFile = new File(mTilesHome, TILE_STYLE + "/" + relTilePath);
                    if (!tileFile.getParentFile().exists()) {
                        tileFile.getParentFile().mkdirs();
                    }
                    if (!tileFile.exists()) {
                        mDownloadTileQueue.put(relTilePath, tileFile);
                    }
                }
            }
            if (mDownloadTileQueue.size() != 0) {
                for (Map.Entry<String, File> dl : mDownloadTileQueue.entrySet()) {
                    mDownloadExecutorService.submit(() -> {
                        try {
                            downloadTile(dl.getKey(), dl.getValue());
                        } catch (IOException e) {
                            LogUtils.error("QueryMapTilesTask:downloadTile ", e);
                        }
                    });
                }
                mDownloadExecutorService.shutdown();
                try {
                    if (!mDownloadExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        mDownloadExecutorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    mDownloadExecutorService.shutdownNow();
                }
            }

            for (int i = yTile - mYTileNum / 2; i <= yTile + mYTileNum / 2; i++) {
                for (int j = xTile - mXTileNum / 2; j <= xTile + mXTileNum / 2; j++) {
                    String relTilePath = zoom + "/" + j + "/" + i + "." + mFormat;
                    File tileFile = new File(mTilesHome, TILE_STYLE + "/" + relTilePath);
                    if (tileFile.exists()) {
                        ImageView tileView = new ImageView();
                        tileView.setImage(new Image(new FileInputStream(tileFile)));

                        double lat = tile2lat(i, zoom);
                        double lon = tile2lon(j, zoom);

                        Point2D pos = coordinateToDisplay(lon, lat, mMapZoom);
                        tileView.setLayoutX(pos.getX());
                        tileView.setLayoutY(pos.getY());
                        tileView.setTranslateX(-mMapZero.getX());
                        tileView.setTranslateY(-mMapZero.getY());
                        mTilesList.add(tileView);
                    } else {
                        // TODO add empty tile?
                    }
                    if (isCancelled()) {
                        break;
                    }
                }
                if (isCancelled()) {
                    break;
                }
            }
            if (isCancelled()) {
                mTilesList.clear();
            }
            return mTilesList;
        }

        private Pair<Integer, Integer> getTileNumber(final double lat, final double lon, final int zoom) {
            int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
            int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
            if (xtile < 0)
                xtile = 0;
            if (xtile >= (1 << zoom))
                xtile = ((1 << zoom) - 1);
            if (ytile < 0)
                ytile = 0;
            if (ytile >= (1 << zoom))
                ytile = ((1 << zoom) - 1);
            return new Pair<>(xtile, ytile);
        }

        double tile2lon(int x, int z) {
            return x / Math.pow(2.0, z) * 360.0 - 180;
        }

        double tile2lat(int y, int z) {
            double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
            return Math.toDegrees(Math.atan(Math.sinh(n)));
        }

        private void downloadTile(String relTilePath, File tileFile) throws IOException {
            // https://api.maptiler.com/maps/openstreetmap/17/68931/45055@2x.jpg?key=IvB2ZEjLZPmCZfC56KxL
            URL url = new URL(mUrl + relTilePath + "?key=IvB2ZEjLZPmCZfC56KxL");
            //LogUtils.log("downloadTile thread = " + Thread.currentThread() + " url = " + url);
            HttpsURLConnection con = setupHttpsRequest(url);
            if (con != null) {
                // opens input stream from the HTTP connection
                InputStream inputStream = con.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(tileFile);

                int bytesRead = -1;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();
            }
        }

        @Override
        public EventHandler<WorkerStateEvent> getSucceedEvent() {
            return event -> {
                LogUtils.log("loadMapData: QueryMapTilesTask succeededEvent");
                stopProgress();
                mTilePane.getChildren().clear();

                mTilePane.getChildren().addAll(mQueryTilesTask.getValue());
                drawShapes();
            };
        }

        @Override
        public EventHandler<WorkerStateEvent> getCancelEvent() {
            return event -> {
                stopProgress();
            };
        }

        @Override
        public EventHandler<WorkerStateEvent> getRunningEvent() {
            return event -> {
                startProgress(false);
            };
        }

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LogUtils.log("initialize");

        loadTileStyleConfig();
        mMapZoom = GISUtils.getIntValue(Config.getInstance().get("zoom", mMapZoom));
        double centerLon = GISUtils.getDoubleValue(Config.getInstance().get("lon", mCenter.getX()));
        double centerLat = GISUtils.getDoubleValue(Config.getInstance().get("lat", mCenter.getY()));
        mCenter = new Point2D(centerLon, centerLat);

        mShow3D = (boolean) Config.getInstance().get("show3D", mShow3D);
        mLocationHistory = (JsonArray) Config.getInstance().get("locationHistory", new JsonArray());
        mLocationHistoryIndex = mLocationHistory.size() - 1;
        mFilterConfig = (JsonObject) Config.getInstance().get("filter", mFilterConfig);
        applyFilterConfig();
        init();
    }

    private void init() {
        LogUtils.log("init");
        String tilesHome = System.getProperty("osm.tiles.path");
        mTilesHome = System.getenv().getOrDefault("OSM_TILES_PATH", tilesHome);
        LogUtils.log("MainController tiles home: " + mTilesHome);

        mWayPolylines = new LinkedHashMap<>();
        mWayPolylines.put(TUNNEL_LAYER_LEVEL, new ArrayList<>());
        mWayPolylines.put(ADMIN_AREA_LAYER_LEVEL, new ArrayList<>());
        mWayPolylines.put(AREA_LAYER_LEVEL, new ArrayList<>());
        mWayPolylines.put(BUILDING_AREA_LAYER_LEVEL, new ArrayList<>());
        mWayPolylines.put(HIDDEN_STREET_LAYER_LEVEL, new ArrayList<>());
        mWayPolylines.put(STREET_LAYER_LEVEL, new ArrayList<>());
        mWayPolylines.put(RAILWAY_LAYER_LEVEL, new ArrayList<>());
        mWayPolylines.put(BRIDGE_LAYER_LEVEL, new ArrayList<>());

        mAreaPolylines = new LinkedHashMap<>();
        mAreaPolylines.put(TUNNEL_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(ADMIN_AREA_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(AREA_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(BUILDING_AREA_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(HIDDEN_STREET_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(STREET_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(RAILWAY_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(BRIDGE_LAYER_LEVEL, new ArrayList<>());

        mCountryPolylines = new LinkedHashMap<>();
        mCountryPolylines.put(ADMIN_AREA_LAYER_LEVEL, new ArrayList<>());

        mNodes = new ArrayList<>();

        mExecutorService = Executors.newFixedThreadPool(8);

        mOSMObjects = new HashMap<>();

        sidePane = new VBox();
        sidePane.setVisible(false);
        sidePane.setStyle("-fx-background-color: white;");

        filterContent = new VBox();
        VBox.setVgrow(filterContent, Priority.ALWAYS);
        createFilterContent();

        searchContent = new VBox();
        VBox.setVgrow(searchContent, Priority.ALWAYS);
        createSearchContent();

        savedNodeListContent = new VBox();
        VBox.setVgrow(savedNodeListContent, Priority.ALWAYS);
        createSavedNodeListContent();

        routeNodeListContent = new VBox();
        VBox.setVgrow(routeNodeListContent, Priority.ALWAYS);
        createRouteNodeListContent();

        TabPane tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        Tab searchTab = new Tab("Search", searchContent);
        searchTab.setStyle("-fx-font-size: 16");
        searchTab.setClosable(false);

        Tab filterTab = new Tab("Filter", filterContent);
        filterTab.setStyle("-fx-font-size: 16");
        filterTab.setClosable(false);

        Tab nodeListTab = new Tab("Nodes", savedNodeListContent);
        nodeListTab.setStyle("-fx-font-size: 16");
        nodeListTab.setClosable(false);

        Tab routeTab = new Tab("Route", routeNodeListContent);
        routeTab.setStyle("-fx-font-size: 16");
        routeTab.setClosable(false);

        tabPane.getTabs().add(searchTab);
        tabPane.getTabs().add(filterTab);
        tabPane.getTabs().add(nodeListTab);
        tabPane.getTabs().add(routeTab);

        sidePane.getChildren().add(tabPane);

        createButtons();
        createLabels();

        zoomLabel.setText(String.valueOf(mMapZoom));

        mContextMenu = new ContextMenu();
        mMenuButtonMenu = new ContextMenu();

        mGPSDot = new Circle();
        mGPSDot.setRadius(30);
        mGPSDot.setFill(Color.TRANSPARENT);
        mGPSDot.setStrokeWidth(2);

        // WURGS - we always need a shape on the map pane to get mouse handler going
        mOutlineRect = new Rectangle();
        mOutlineRect.setStroke(Color.TRANSPARENT);
        mOutlineRect.setFill(Color.TRANSPARENT);
        mOutlineRect.setStrokeWidth(1);
        mOutlineRect.setVisible(true);

        mCalcPoint = new Circle();
        mCalcPoint.setCenterX(0);
        mCalcPoint.setCenterY(0);
        mCalcPoint.setRadius(0);
        mCalcPoint.setVisible(false);

        mRotate = new Rotate(-ROTATE_X_VALUE, Rotate.X_AXIS);
        mZRotate = new Rotate();

        mainPane = new Pane();
        mainStackPane.getChildren().add(mainPane);

        mProgress = new ProgressIndicator();
        rootPane.getChildren().add(mProgress);
        mProgress.setVisible(false);

        mProgressStop = new Button();
        mProgressStop.setText("Cancel");
        rootPane.getChildren().add(mProgressStop);
        mProgressStop.setTranslateY(mProgress.getHeight());
        mProgressStop.setVisible(false);

        topPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.2), null, null)));
        infoPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.2), null, null)));
        bottomPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.2), null, null)));

        mainPane.getChildren().add(mTilePane);
        mainPane.getChildren().add(mAreaPane);
        mainPane.getChildren().add(mMapPane);
        mainPane.getChildren().add(mCountryPane);
        mainPane.getChildren().add(mLabelPane);

        mTransformPanes.add(mTilePane);
        mTransformPanes.add(mAreaPane);
        mTransformPanes.add(mMapPane);
        mTransformPanes.add(mCountryPane);

        mainPane.getChildren().add(mNodePane);
        mainPane.getChildren().add(mRoutingPane);
        mainPane.getChildren().add(mRoutingNodePane);
        mainPane.getChildren().add(mTrackingPane);

        // pass events through
        mNodePane.setDisable(true);
        mRoutingPane.setDisable(true);
        mRoutingNodePane.setDisable(true);
        mTrackingPane.setDisable(true);
        mAreaPane.setDisable(true);
        mTilePane.setDisable(true);
        mCountryPane.setDisable(true);
        mLabelPane.setDisable(true);

        borderPane.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                switch (event.getCode()) {
                    case ESCAPE:
                        mSelectdShape = null;
                        mSelectdOSMId = -1;
                        mSelectedEdge = null;
                        mSelectedEdgeShape = null;
                        drawShapes();
                        break;
                }
            }
        });

        restoreSavedNodes();
        if (QueryController.getInstance().hasSavedRoutes()) {
            QueryController.getInstance().loadRoute(this);
        } else {
            restoreRoutingNodes();
        }
    }

    private void createLabels() {
        DropShadow shadow = new DropShadow(
                BlurType.ONE_PASS_BOX, Color.BLACK, 2, 2, 0, 0);

        zoomLabel.setTextFill(Color.WHITE);
        zoomLabel.setEffect(shadow);
        zoomTitle.setTextFill(Color.WHITE);
        zoomTitle.setEffect(shadow);

        speedLabel.setTextFill(Color.WHITE);
        speedLabel.setEffect(shadow);

        altLabel.setTextFill(Color.WHITE);
        altLabel.setEffect(shadow);

        posLabel.setTextFill(Color.WHITE);
        posLabel.setEffect(shadow);
        posTitle.setTextFill(Color.WHITE);
        posTitle.setEffect(shadow);

        wayLabel.setTextFill(Color.WHITE);
        wayLabel.setEffect(shadow);

        infoLabel.setTextFill(Color.WHITE);
        infoLabel.setEffect(shadow);
        infoTitle.setTextFill(Color.WHITE);
        infoTitle.setEffect(shadow);
    }

    private void createButtons() {
        quitButton.setGraphic(new ImageView(new Image("/images/ui/quit.png")));
        //quitButton.setShape(new Circle(40));
        quitButton.setOnAction(e -> {
            mExecutorService.shutdown();
            try {
                if (!mExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    mExecutorService.shutdownNow();
                }
            } catch (InterruptedException ex) {
                mExecutorService.shutdownNow();
            }
            Platform.exit();
        });

        searchButton.setGraphic(new ImageView(new Image("/images/ui/map-search.png")));
        //searchButton.setShape(new Circle(40));
        searchButton.setOnAction(e -> {
            if (!mSidePaneExpanded) {
                showSidePane();
            } else {
                hideSidePane();
            }
        });

        menuButton.setGraphic(new ImageView(new Image("/images/ui/menu.png")));
        //menuButton.setShape(new Circle(30));
        menuButton.setOnMouseClicked(e -> {
            if (!mMapLoading) {
                buildButtonMenu();
                mMenuButtonMenu.show(menuButton, e.getScreenX(), e.getScreenY());
            }
        });

        zoomInButton.setGraphic(new ImageView(new Image("/images/ui/plus.png")));
        //zoomInButton.setShape(new Circle(40));
        zoomInButton.setOnAction(e -> {
            int zoom = mMapZoom + 1;
            zoom = Math.min(MAX_ZOOM, zoom);
            if (zoom != mMapZoom) {
                if (!mMapLoading) {
                    mMapZoom = zoom;
                    zoomLabel.setText(String.valueOf(mMapZoom));
                    calcMapCenterPos();
                    calcMapZeroPos(true);
                    maybeLoadMapData(null);
                }
            }
        });
        zoomOutButton.setGraphic(new ImageView(new Image("/images/ui/minus.png")));
        //zoomOutButton.setShape(new Circle(30));
        zoomOutButton.setOnAction(e -> {
            int zoom = mMapZoom - 1;
            zoom = Math.max(MIN_ZOOM, zoom);
            if (!mMapLoading) {
                if (zoom != mMapZoom) {
                    mMapZoom = zoom;
                    zoomLabel.setText(String.valueOf(mMapZoom));
                    calcMapCenterPos();
                    calcMapZeroPos(true);
                    maybeLoadMapData(null);
                }
            }
        });

        trackModeButton.setGraphic(new ImageView(new Image(mTrackMode ? "/images/ui/gps.png" : "/images/ui/gps-circle.png")));
        //trackModeButton.setShape(new Circle(30));
        trackModeButton.setOnAction(event -> {
            if (!mMapLoading) {
                if (!mTrackReplayMode) {
                    mTrackMode = trackModeButton.isSelected();
                    updateTrackMode();
                }
            }
        });

        startReplayButton.setGraphic(new ImageView(new Image("/images/ui/play.png")));
        //startReplayButton.setShape(new Circle(30));
        startReplayButton.setOnAction(event -> {
            if (mTrackReplayThread != null) {
                mTrackReplayThread.startThread();
                startReplayButton.setDisable(true);
                stopReplayButton.setDisable(false);
                pauseReplayButton.setDisable(false);
                stepReplayButton.setDisable(false);
            } else {
                if (mCurrentTrackFile != null) {
                    mTrackReplayThread = new TrackReplayThread();
                    if (!mTrackReplayThread.setupReplay(mCurrentTrackFile, this)) {
                        LogUtils.error("failed to setup replay thread");
                        mTrackReplayThread = null;
                    } else {
                        mTrackReplayMode = true;
                        mTrackReplayThread.startThread();
                        startReplayButton.setDisable(true);
                        stopReplayButton.setDisable(false);
                        pauseReplayButton.setDisable(false);
                        stepReplayButton.setDisable(false);
                    }
                }
            }
        });
        stopReplayButton.setGraphic(new ImageView(new Image("/images/ui/stop.png")));
        //stopReplayButton.setShape(new Circle(30));
        stopReplayButton.setOnAction(event -> {
            startReplayButton.setDisable(false);
            stopReplayButton.setDisable(true);
            pauseReplayButton.setDisable(true);
            stepReplayButton.setDisable(true);

            stopReplay();
            resetTracking();
            drawShapes();
        });
        pauseReplayButton.setGraphic(new ImageView(new Image("/images/ui/pause.png")));
        //pauseReplayButton.setShape(new Circle(30));
        pauseReplayButton.setOnAction(event -> {
            if (mTrackReplayThread != null) {
                mTrackReplayThread.pauseThread();
            }
        });
        stepReplayButton.setGraphic(new ImageView(new Image("/images/ui/next.png")));
        //stepReplayButton.setShape(new Circle(30));
        stepReplayButton.setOnAction(event -> {
            if (mTrackReplayThread != null) {
                mTrackReplayThread.stepThread();
            }
        });
    }

    private void createSearchContent() {
        mQueryField = new TextField();
        mQueryField.setPromptText("Search...");
        mQueryField.setMinHeight(48);
        mQueryField.setStyle("-fx-font-size: 16");

        mQueryField.setOnKeyReleased(event -> {
            mQueryText = mQueryField.getText();
            updateListContent();
        });
        searchContent.getChildren().add(mQueryField);

        HBox filterSelect = new HBox();
        filterSelect.setPadding(new Insets(10, 10, 10, 10));

        ToggleGroup filterGroup = new ToggleGroup();

        RadioButton poiFilter = new RadioButton("POI");
        poiFilter.setToggleGroup(filterGroup);
        poiFilter.setOnAction(event -> {
            mFilterHeader.setVisible(false);
            mFilterType = FilterType.POI;
            updateListContent();
        });

        RadioButton addressFilter = new RadioButton("Address");
        addressFilter.setPadding(new Insets(0, 0, 0, 10));
        addressFilter.setToggleGroup(filterGroup);
        addressFilter.setOnAction(event -> {
            mFilterHeader.setVisible(false);
            mFilterType = FilterType.ADDRESS;
            updateListContent();
        });

        RadioButton cityFilter = new RadioButton("City");
        cityFilter.setPadding(new Insets(0, 0, 0, 10));
        cityFilter.setToggleGroup(filterGroup);
        cityFilter.setOnAction(event -> {
            mFilterHeader.setVisible(false);
            mFilterType = FilterType.CITY;
            updateListContent();
        });

        RadioButton localFilter = new RadioButton("Local");
        localFilter.setPadding(new Insets(0, 0, 0, 10));
        localFilter.setToggleGroup(filterGroup);
        localFilter.setOnAction(event -> {
            mFilterHeader.getItems().clear();
            // show area popup
            mFilterHeader.setVisible(true);
            JsonArray adminDataList = getAdminAreaListAtPos(mCenter);
            for (int i = 0; i < adminDataList.size(); i++) {
                JsonObject adminData = (JsonObject) adminDataList.get(i);
                long osmId = GISUtils.getLongValue(adminData.get("osmId"));
                int adminLevel = GISUtils.getIntValue(adminData.get("adminLevel"));
                String name = (String) adminData.get("name");
                //LogUtils.log(name + " " + adminLevel);
                MenuItem menuItem = new MenuItem(name);
                menuItem.setStyle("-fx-font-size: 16");
                menuItem.setUserData(osmId);
                menuItem.setOnAction(e -> {
                    long adminId = (long) menuItem.getUserData();
                    mFilterHeader.setText(menuItem.getText());
                    // TODO this is blocking ui
                    mAdminIdList = QueryController.getInstance().getAdminAreaChildren(adminId);
                    mAdminIdList.add(adminId);
                    updateListContent();
                });
                mFilterHeader.getItems().add(menuItem);

                if (i == 0) {
                    mAdminIdList = new JsonArray();
                    mAdminIdList.add(osmId);
                    mFilterHeader.setText(menuItem.getText());
                }
            }
            mFilterType = FilterType.LOCAL;
            updateListContent();
        });

        poiFilter.setSelected(true);

        filterSelect.getChildren().add(poiFilter);
        filterSelect.getChildren().add(addressFilter);
        filterSelect.getChildren().add(cityFilter);
        filterSelect.getChildren().add(localFilter);

        searchContent.getChildren().add(filterSelect);

        mFilterHeader = new MenuButton("Area                            ");
        mFilterHeader.setStyle("-fx-font-size: 16");
        mFilterHeader.setVisible(false);
        searchContent.getChildren().add(mFilterHeader);

        mQueryListView = new ListView<>();
        mQueryListView.setItems(mQueryItems);
        mQueryListView.setCellFactory(queryListView -> new QueryListViewCell());
        mQueryListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<QueryItem>() {
            @Override
            public void changed(ObservableValue<? extends QueryItem> observable, QueryItem oldValue, QueryItem newValue) {
                if (newValue != null) {
                    JsonArray coords = newValue.getCoords();
                    //infoLabel.setText(newValue.getName());
                    centerMapOnCoordinatesWithZoom((double) coords.get(0), (double) coords.get(1), 17);
                    addToLocationHistory(coords);
                }
            }
        });

        StackPane listViewPane = new StackPane();
        searchContent.getChildren().add(listViewPane);
        VBox.setVgrow(listViewPane, Priority.ALWAYS);

        mQueryListProgress = new ProgressIndicator();
        listViewPane.getChildren().add(mQueryListView);
        listViewPane.getChildren().add(mQueryListProgress);
        mQueryListProgress.setVisible(false);
    }

    private void createSavedNodeListContent() {
        mSavedNodesListView = new ListView<>();
        mSavedNodesListView.setItems(mSavedNodesListItems);
        mSavedNodesListView.setCellFactory(queryListView -> new NodeListViewCell(this));
        mSavedNodesListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<RoutingNode>() {
            @Override
            public void changed(ObservableValue<? extends RoutingNode> observable, RoutingNode oldValue, RoutingNode newValue) {
                if (newValue != null) {
                    Point2D coords = newValue.getCoordsPos();
                    centerMapOnCoordinatesWithZoom(coords.getX(), coords.getY(), 17);
                }
            }
        });

        savedNodeListContent.getChildren().add(mSavedNodesListView);
    }

    private void createRouteNodeListContent() {
        HBox routeButtons = new HBox();
        routeButtons.setPadding(new Insets(10, 10, 10, 10));
        Button calcRouteButton = new Button();
        calcRouteButton.setStyle("-fx-font-size: 16");
        calcRouteButton.setText("Calc");
        calcRouteButton.setOnAction(event -> {
            if (isCalcRoutePossible()) {
                calcRoute(getRoutingStart(), getRoutingEnd());
            }
        });
        routeButtons.getChildren().add(calcRouteButton);

        Button revertRouteButton = new Button();
        revertRouteButton.setStyle("-fx-font-size: 16");
        revertRouteButton.setText("Revert");
        revertRouteButton.setOnAction(event -> {
            if (isCalcRoutePossible()) {
                RoutingNode startNode = getRoutingStart();
                RoutingNode endMode = getRoutingEnd();

                startNode.revertType();
                endMode.revertType();

                updateRouteNodeListContent();
            }
        });
        routeButtons.getChildren().add(revertRouteButton);

        Button clearRouteButton = new Button();
        clearRouteButton.setText("Clear");
        clearRouteButton.setStyle("-fx-font-size: 16");
        clearRouteButton.setOnAction(event -> {
            clearRoute();
            drawShapes();
        });
        routeButtons.getChildren().add(clearRouteButton);
        routeNodeListContent.getChildren().add(routeButtons);

        mRouteNodesListView = new ListView<>();
        mRouteNodesListView.setItems(mRouteNodesListItems);
        mRouteNodesListView.setCellFactory(queryListView -> new NodeListViewCell(this));
        mRouteNodesListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<RoutingNode>() {
            @Override
            public void changed(ObservableValue<? extends RoutingNode> observable, RoutingNode oldValue, RoutingNode newValue) {
                if (newValue != null) {
                    Point2D coords = newValue.getCoordsPos();
                    centerMapOnCoordinatesWithZoom(coords.getX(), coords.getY(), 17);
                }
            }
        });
        routeNodeListContent.getChildren().add(mRouteNodesListView);

        mRouteAContent = new VBox();
        VBox.setVgrow(mRouteAContent, Priority.ALWAYS);

        TabPane tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Tab route1Tab = new Tab(RouteUtils.TYPE.FASTEST.name(), mRouteAContent);
        route1Tab.setStyle(String.format("-fx-font-size: 16; -fx-text-base-color: %s;",
                Route.Companion.getRouteCSSColor(RouteUtils.TYPE.FASTEST)));
        route1Tab.setClosable(false);

        mRouteBContent = new VBox();
        VBox.setVgrow(mRouteBContent, Priority.ALWAYS);

        Tab route2Tab = new Tab(RouteUtils.TYPE.ALT.name(), mRouteBContent);
        route2Tab.setStyle(String.format("-fx-font-size: 16; -fx-text-base-color: %s;",
                Route.Companion.getRouteCSSColor(RouteUtils.TYPE.ALT)));
        route2Tab.setClosable(false);

        tabPane.getTabs().add(route1Tab);
        tabPane.getTabs().add(route2Tab);

        createRouteAListContent();
        createRouteBListContent();

        routeNodeListContent.getChildren().add(tabPane);
    }

    private void createFilterContent() {
        CheckBox tilePaneVisible = new CheckBox("Tiles");
        tilePaneVisible.setStyle("-fx-font-size: 16");
        tilePaneVisible.setPadding(new Insets(3, 0, 3, 0));
        tilePaneVisible.setSelected((boolean) mFilterConfig.get("tile"));
        tilePaneVisible.setOnAction(event -> {
            if (tilePaneVisible.isSelected()) {
                mTilePane.setVisible(true);
            } else {
                mTilePane.setVisible(false);
            }
            loadMapData(null);
            mFilterConfig.put("tile", tilePaneVisible.isSelected());
            saveCurrentFilter();
        });
        CheckBox countryPaneVisible = new CheckBox("Countries");
        countryPaneVisible.setStyle("-fx-font-size: 16");
        countryPaneVisible.setPadding(new Insets(3, 0, 3, 0));
        countryPaneVisible.setSelected((Boolean) mFilterConfig.get("country"));
        countryPaneVisible.setOnAction(event -> {
            if (countryPaneVisible.isSelected()) {
                mCountryPane.setVisible(true);
                loadMapData(null);
            } else {
                mCountryPane.setVisible(false);
            }
            mFilterConfig.put("country", countryPaneVisible.isSelected());
            saveCurrentFilter();
        });
        CheckBox poiPaneVisible = new CheckBox("POIs");
        poiPaneVisible.setStyle("-fx-font-size: 16");
        poiPaneVisible.setPadding(new Insets(3, 0, 3, 0));
        poiPaneVisible.setSelected((Boolean) mFilterConfig.get("poi"));
        poiPaneVisible.setOnAction(event -> {
            if (poiPaneVisible.isSelected()) {
                mNodePane.setVisible(true);
                loadMapData(null);
            } else {
                mNodePane.setVisible(false);
            }
            mFilterConfig.put("poi", poiPaneVisible.isSelected());
            saveCurrentFilter();
        });

        CheckBox areaPaneVisible = new CheckBox("Areas");
        areaPaneVisible.setStyle("-fx-font-size: 16");
        areaPaneVisible.setPadding(new Insets(3, 0, 3, 0));
        areaPaneVisible.setSelected((Boolean) mFilterConfig.get("area"));
        areaPaneVisible.setOnAction(event -> {
            if (areaPaneVisible.isSelected()) {
                mAreaPane.setVisible(true);
                loadMapData(null);
            } else {
                mAreaPane.setVisible(false);
            }
            mFilterConfig.put("area", areaPaneVisible.isSelected());
            saveCurrentFilter();
        });

        CheckBox labelPaneVisible = new CheckBox("Labels");
        labelPaneVisible.setStyle("-fx-font-size: 16");
        labelPaneVisible.setPadding(new Insets(3, 0, 3, 0));
        labelPaneVisible.setSelected((Boolean) mFilterConfig.get("label"));
        labelPaneVisible.setOnAction(event -> {
            if (labelPaneVisible.isSelected()) {
                mLabelPane.setVisible(true);
                loadMapData(null);
            } else {
                mLabelPane.setVisible(false);
            }
            mFilterConfig.put("label", labelPaneVisible.isSelected());
            saveCurrentFilter();
        });

        CheckBox waysVisible = new CheckBox("Ways");
        waysVisible.setStyle("-fx-font-size: 16");
        waysVisible.setPadding(new Insets(3, 0, 3, 0));
        waysVisible.setSelected((Boolean) mFilterConfig.get("way"));
        waysVisible.setOnAction(event -> {
            if (waysVisible.isSelected()) {
                mTransparentWays = false;
            } else {
                mTransparentWays = true;
            }
            loadMapData(null);
            mFilterConfig.put("way", waysVisible.isSelected());
            saveCurrentFilter();
        });

        filterContent.setPadding(new Insets(10, 10, 10, 10));
        filterContent.getChildren().add(tilePaneVisible);
        filterContent.getChildren().add(countryPaneVisible);
        filterContent.getChildren().add(poiPaneVisible);
        filterContent.getChildren().add(areaPaneVisible);
        filterContent.getChildren().add(labelPaneVisible);
        filterContent.getChildren().add(waysVisible);
    }

    public void stop() {
        stopGPSTracking();
        Config.getInstance().put("zoom", mMapZoom);
        Config.getInstance().put("lon", mCenter.getX());
        Config.getInstance().put("lat", mCenter.getY());
        Config.getInstance().put("show3D", mShow3D);
        mExecutorService.shutdown();
    }

    protected void setStage(Stage primaryStage) {
        mPrimaryStage = primaryStage;
    }

    protected void setScene(Scene scene) {
        mScene = scene;
        mScene.widthProperty().addListener((observableValue, oldSceneWidth, newSceneWidth) -> {
            calcMapCenterPos();
            calcMapZeroPos(true);
            maybeLoadMapData(null);
        });
        mScene.heightProperty().addListener((observableValue, oldSceneHeight, newSceneHeight) -> {
            calcMapCenterPos();
            calcMapZeroPos(true);
            maybeLoadMapData(null);
        });

        // NEVER EVER add eventlistener to anythign else
        // needed for correct x,y translation in 3d mode
        mMapPane.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseHandler);
        mMapPane.addEventFilter(MouseEvent.MOUSE_CLICKED, mouseHandler);
        mMapPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseHandler);
    }

    public void addToOSMCache(long osmId, JsonObject osmObject) {
        mOSMObjects.put(osmId, osmObject);
    }

    private Popup createPopup(String text) {
        Label label = new Label(text);
        Popup popup = new Popup();
        label.setStyle("-fx-font-size: 20; -fx-background-color: white;");
        popup.getContent().add(label);
        label.setMinWidth(250);
        label.setMinHeight(200);
        return popup;
    }

    private List<Integer> getStreetTypeListForZoom() {
        if (mMapZoom <= 8) {
            return null;
        } else if (mMapZoom <= 10) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.STREET_TYPE_MOTORWAY,
                    OSMUtils.STREET_TYPE_MOTORWAY_LINK,
                    OSMUtils.STREET_TYPE_TRUNK,
                    OSMUtils.STREET_TYPE_TRUNK_LINK,
                    OSMUtils.STREET_TYPE_PRIMARY,
                    OSMUtils.STREET_TYPE_PRIMARY_LINK);
            return typeFilterList;
        } else if (mMapZoom <= 13) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.STREET_TYPE_MOTORWAY,
                    OSMUtils.STREET_TYPE_MOTORWAY_LINK,
                    OSMUtils.STREET_TYPE_TRUNK,
                    OSMUtils.STREET_TYPE_TRUNK_LINK,
                    OSMUtils.STREET_TYPE_PRIMARY,
                    OSMUtils.STREET_TYPE_PRIMARY_LINK,
                    OSMUtils.STREET_TYPE_SECONDARY,
                    OSMUtils.STREET_TYPE_SECONDARY_LINK,
                    OSMUtils.STREET_TYPE_TERTIARY,
                    OSMUtils.STREET_TYPE_TERTIARY_LINK);
            return typeFilterList;
        } else if (mMapZoom <= MAX_ZOOM) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.STREET_TYPE_MOTORWAY,
                    OSMUtils.STREET_TYPE_MOTORWAY_LINK,
                    OSMUtils.STREET_TYPE_TRUNK,
                    OSMUtils.STREET_TYPE_TRUNK_LINK,
                    OSMUtils.STREET_TYPE_PRIMARY,
                    OSMUtils.STREET_TYPE_PRIMARY_LINK,
                    OSMUtils.STREET_TYPE_SECONDARY,
                    OSMUtils.STREET_TYPE_SECONDARY_LINK,
                    OSMUtils.STREET_TYPE_TERTIARY,
                    OSMUtils.STREET_TYPE_TERTIARY_LINK,
                    OSMUtils.STREET_TYPE_RESIDENTIAL,
                    OSMUtils.STREET_TYPE_ROAD,
                    OSMUtils.STREET_TYPE_UNCLASSIFIED,
                    OSMUtils.STREET_TYPE_SERVICE,
                    OSMUtils.STREET_TYPE_TRACK);
            return typeFilterList;
        } else {
            return null;
        }
    }

    private String getAdminLevelListForZoom() {
        if (mMapZoom <= 8) {
            return "(2, 4)";
        } else if (mMapZoom <= MAX_ZOOM) {
            return "(2, 4, 6)";
        /*} else if (mMapZoom <= MAX_ZOOM) {
            return "(4, 6, 8)";*/
        } else {
            return null;
        }
    }

    private List<Integer> getAreaTypeListForZoom(boolean transparentWays) {
        if (transparentWays) {
            if (mMapZoom >= 14) {
                List<Integer> typeFilterList = new ArrayList<>();
                Collections.addAll(typeFilterList,
                        OSMUtils.AREA_TYPE_BUILDING);
                return typeFilterList;
            } else {
                return null;
            }
        } else {
            if (mMapZoom <= 11) {
                return null;
            } else if (mMapZoom <= 12) {
                List<Integer> typeFilterList = new ArrayList<>();
                Collections.addAll(typeFilterList, OSMUtils.AREA_TYPE_LANDUSE,
                        OSMUtils.AREA_TYPE_NATURAL,
                        OSMUtils.AREA_TYPE_AEROWAY,
                        OSMUtils.AREA_TYPE_RAILWAY,
                        OSMUtils.AREA_TYPE_WATER);
                return typeFilterList;
            } else if (mMapZoom <= 16) {
                List<Integer> typeFilterList = new ArrayList<>();
                Collections.addAll(typeFilterList, OSMUtils.AREA_TYPE_LANDUSE,
                        OSMUtils.AREA_TYPE_NATURAL,
                        OSMUtils.AREA_TYPE_HIGHWAY_AREA,
                        OSMUtils.AREA_TYPE_AEROWAY,
                        OSMUtils.AREA_TYPE_RAILWAY,
                        OSMUtils.AREA_TYPE_TOURISM,
                        OSMUtils.AREA_TYPE_LEISURE,
                        OSMUtils.AREA_TYPE_WATER);
                return typeFilterList;
            } else if (mMapZoom <= MAX_ZOOM) {
                List<Integer> typeFilterList = new ArrayList<>();
                Collections.addAll(typeFilterList, OSMUtils.AREA_TYPE_LANDUSE,
                        OSMUtils.AREA_TYPE_NATURAL,
                        OSMUtils.AREA_TYPE_HIGHWAY_AREA,
                        OSMUtils.AREA_TYPE_AEROWAY,
                        OSMUtils.AREA_TYPE_RAILWAY,
                        OSMUtils.AREA_TYPE_TOURISM,
                        OSMUtils.AREA_TYPE_LEISURE,
                        OSMUtils.AREA_TYPE_BUILDING,
                        OSMUtils.AREA_TYPE_WATER);
                return typeFilterList;
            } else {
                return null;
            }
        }
    }

    public double getSimplifyToleranceForZoom() {
        if (mMapZoom <= 6) {
            return 200.0;
        } else if (mMapZoom <= 8) {
            return 120.0;
        } else if (mMapZoom <= 10) {
            return 80.0;
        } else if (mMapZoom <= 12) {
            return 40.0;
        } else if (mMapZoom <= 14) {
            return 30.0;
        } else if (mMapZoom <= 15) {
            return 20.0;
        } else if (mMapZoom <= 16) {
            return 10.0;
        } else {
            return 0;
        }
    }

    private int getAreaMinSizeForZoom() {
        if (mMapZoom <= 12) {
            return 100000;
        } else if (mMapZoom <= 13) {
            return 20000;
        } else if (mMapZoom <= 14) {
            return 10000;
        } else if (mMapZoom <= 16) {
            return 2000;
        } else if (mMapZoom <= MAX_ZOOM) {
            return 0;
        } else {
            return 0;
        }
    }

    private int getPlaceLabelFontSizeForZoom(String placeType) {
        // TODO match place type eg city
        if (mMapZoom <= 12) {
            return 11;
        } else if (mMapZoom <= 13) {
            return 12;
        } else if (mMapZoom <= 14) {
            return 14;
        } else if (mMapZoom <= MAX_ZOOM) {
            return 16;
        } else {
            return 16;
        }
    }

    private int getCountryLabelFontSizeForZoom() {
        // on 11  countries start
        if (mMapZoom <= 10) {
            return 18;
        } else if (mMapZoom <= 12) {
            return 16;
        } else {
            return 16;
        }
    }

    private List<Integer> getPoiTypeListForZoom() {
        return OSMUtils.SELECT_POI_TYPE;
    }

    private void doUpdateMapData(List<Double> bbox, Runnable doAfter) {
        LogUtils.log("doUpdateMapData");
        long t = System.currentTimeMillis();

        mMapPane.getChildren().clear();
        mRoutingPane.getChildren().clear();
        mTrackingPane.getChildren().clear();
        mCountryPane.getChildren().clear();

        if (mSelectdOSMId != -1) {
            mSelectdShape = findShapeOfOSMId(mSelectdOSMId);
            if (mSelectdShape == null) {
                mSelectdOSMId = -1;
            } else {
                mSelectdShape.setSelected();
            }
        }

        if (mTrackingOSMId != -1) {
            mTrackingShape = findShapeOfOSMId(mTrackingOSMId);
            if (mTrackingShape == null) {
                mTrackingOSMId = -1;
            } else {
                mTrackingShape.setTracking();
            }
        }
        for (List<Node> polyList : mCountryPolylines.values()) {
            mCountryPane.getChildren().addAll(polyList);
        }
        for (List<Node> polyList : mWayPolylines.values()) {
            mMapPane.getChildren().addAll(polyList);
        }

        if (mMapZoom >= 16) {
            if (mSelectdShape != null) {
                mRoutingPane.getChildren().add(mSelectdShape.getShape());
            }
            if (mSelectedEdge != null) {
                createSelectedEdgeShape();
                mRoutingPane.getChildren().add(mSelectedEdgeShape);
            }
            if (mTrackingShape != null) {
                mTrackingPane.getChildren().add(mTrackingShape.getShape());
            }
        }

        if (mTrackMode || mTrackReplayMode) {
            if (isPositionVisible(mMapGPSPos)) {
                addGPSDot();
            }
            if (mMapZoom >= 16) {
                if (mPredictionWays.size() != 0) {
                    for (Long osmId : mPredictionWays) {
                        OSMShape s = findShapeOfOSMId(osmId);
                        if (s != null) {
                            s.getShape().setStrokeWidth(2);
                            s.getShape().setStroke(Color.GREEN);
                            mTrackingPane.getChildren().add(s.getShape());
                        }
                    }
                }
            }
        }

        mTrackingPane.getChildren().add(mCalcPoint);

        if (mMapZoom >= 14) {
            // updateRoutingNode depends on calc point so dont do before added
            for (RoutingNode node : mRoutingNodes) {
                updateRoutingNode(node, true);
            }
            for (RoutingNode node : mSavedNodes) {
                updateRoutingNode(node, true);
            }
        }

        // we want transparent buildings to select
        mLoadAreaTask = new LoadAreaTask(bbox);
        for (List<Node> polyList : mAreaPolylines.values()) {
            polyList.clear();
        }
        mLoadAreaTask.submit(mExecutorService, mMapZoom);

        if (mNodePane.isVisible()) {
            mLoadPOITask = new LoadPOITask(bbox);
            mLoadPOITask.submit(mExecutorService, mMapZoom);
        }

        if (mTilePane.isVisible()) {
            mQueryTilesTask = new QueryMapTilesTask();
            mQueryTilesTask.submit(mExecutorService, mMapZoom);
            // task will call drawShape at end whoch will add the route
            //mRoutingPane.getChildren().addAll(mRoutePolylineList);
        } else {
            mRoutingPane.getChildren().addAll(mRoutePolylineList);
        }

        if (mLabelPane.isVisible()) {
            mLoadLabelTask = new LoadLabelTask(bbox);
            mLoadLabelTask.submit(mExecutorService, mMapZoom);
        }

        stopProgress();

        if (doAfter != null) {
            doAfter.run();
        }
        LogUtils.log("doUpdateMapData time = " + (System.currentTimeMillis() - t));
    }

    private void doLoadMapData(List<Double> bbox) {
        LogUtils.log("doLoadMapData");
        mMapLoading = true;

        long t = System.currentTimeMillis();
        if (mCountryPane.isVisible()) {
            // always show countries
            QueryController.getInstance().getAdminAreasInBboxWithGeom(bbox.get(0), bbox.get(1),
                    bbox.get(2), bbox.get(3), getAdminLevelListForZoom(), getSimplifyToleranceForZoom(),
                    mCountryPolylines, MainController.this);
            LogUtils.log("doLoadMapData getCountriesInBboxWithGeom time = " + (System.currentTimeMillis() - t));
        }

        if (mMapZoom >= 10) {
            t = System.currentTimeMillis();
            QueryController.getInstance().getWaysInBboxWithGeom(bbox.get(0), bbox.get(1),
                    bbox.get(2), bbox.get(3), getStreetTypeListForZoom(), 0 /*,getSimplifyToleranceForZoom()*/, mWayPolylines,
                    MainController.this);
            LogUtils.log("doLoadMapData getWaysInBboxWithGeom time = " + (System.currentTimeMillis() - t));
        }

        t = System.currentTimeMillis();
        QueryController.getInstance().getRoutes(mRoutePolylineList, this);
        LogUtils.log("doLoadMapData getRoutes time = " + (System.currentTimeMillis() - t));

        mWayPolylines.get(MainController.STREET_LAYER_LEVEL).add(mOutlineRect);
        updateFakeShapes();
    }

    public void loadFirstMapData() {
        startProgress(false);
        calcMapCenterPos();
        calcMapZeroPos(true);
        loadMapData(null);
    }

    private void maybeLoadMapData(Runnable doAfter) {
        BoundingBox bbox = getVisibleBBox();
        if (!mFetchBBox.contains(bbox)) {
            if (!mMapLoading) {
                loadMapData(doAfter);
            }
        } else {
            drawShapes();
            if (doAfter != null) {
                doAfter.run();
            }
        }
    }

    public void loadMapData(Runnable doAfter) {
        if (mLoadMapDataTask != null) {
            mLoadMapDataTask.cancel(true);
            mLoadMapDataTask = null;
        }
        LogUtils.log("loadMapData " + mMapZoom);

        mVisibleBBox = getVisibleBBox();
        mFetchBBox = getVisibleBBoxWithMargin(mVisibleBBox);

        if (mLoadPOITask != null) {
            LogUtils.log("loadMapData: LoadPOITask cancel");
            mLoadPOITask.cancel(true);
            mLoadPOITask = null;
        }

        if (mLoadAreaTask != null) {
            LogUtils.log("loadMapData: LoadAreaTask cancel");
            mLoadAreaTask.cancel(true);
            mLoadAreaTask = null;
        }

        if (mQueryTilesTask != null) {
            LogUtils.log("loadMapData: QueryTilesTask cancel");
            mQueryTilesTask.cancel(true);
            mQueryTilesTask = null;
        }

        if (mLoadLabelTask != null) {
            LogUtils.log("loadMapData: LoadLabelTask cancel");
            mLoadLabelTask.cancel(true);
            mLoadLabelTask = null;
        }

        mOSMObjects.clear();
        for (List<Node> polyList : mWayPolylines.values()) {
            polyList.clear();
        }
        for (List<Node> polyList : mCountryPolylines.values()) {
            polyList.clear();
        }

        List<Double> bbox = getBBoxInDeg(mFetchBBox);
        mLoadMapDataTask = new LoadMapDataTask(bbox, doAfter);
        mLoadMapDataTask.submit(mExecutorService, mMapZoom);
    }

    private synchronized void drawShapes() {
        mNodePane.getChildren().clear();
        mRoutingPane.getChildren().clear();
        mRoutingNodePane.getChildren().clear();
        mTrackingPane.getChildren().clear();
        mLabelPane.getChildren().clear();

        if (mTrackingShape != null) {
            mTrackingShape.getShape().setTranslateX(-mMapZero.getX());
            mTrackingShape.getShape().setTranslateY(-mMapZero.getY());
        }
        if (mSelectdShape != null) {
            mRoutingPane.getChildren().add(mSelectdShape.getShape());
        }
        if (mSelectedEdgeShape != null) {
            mRoutingPane.getChildren().add(mSelectedEdgeShape);
        }

        mRoutingPane.getChildren().addAll(mRoutePolylineList);

        if (mTrackingShape != null) {
            mTrackingPane.getChildren().add(mTrackingShape.getShape());
        }
        if (mTrackMode || mTrackReplayMode) {
            if (isPositionVisible(mMapGPSPos)) {
                addGPSDot();
            }
            if (mPredictionWays.size() != 0) {
                for (Long osmId : mPredictionWays) {
                    OSMShape s = findShapeOfOSMId(osmId);
                    if (s != null) {
                        s.getShape().setStrokeWidth(2);
                        s.getShape().setStroke(Color.GREEN);
                        mTrackingPane.getChildren().add(s.getShape());
                    }
                }
            }
        }
        mTrackingPane.getChildren().add(mCalcPoint);

        if (mMapZoom >= 17) {
            for (OSMImageView node : mNodes) {
                updatePOINode(node, false);
            }
            mNodePane.getChildren().addAll(mNodes);
        }

        if (mMapZoom >= 14) {
            // updateSize must be true cause draw shapes is called after adding a new node
            for (RoutingNode node : mRoutingNodes) {
                updateRoutingNode(node, true);
            }
            mRoutingNodePane.getChildren().addAll(mRoutingNodes);

            for (RoutingNode node : mSavedNodes) {
                updateRoutingNode(node, true);
            }
            mRoutingNodePane.getChildren().addAll(mSavedNodes);
        }
        for (OSMTextLabel label : mLabelShapeList) {
            updateLabelShape(label);
        }
        mLabelPane.getChildren().addAll(mLabelShapeList);

        for (Node s : mNodePane.getChildren()) {
            s.setTranslateX(-mMapZero.getX());
            s.setTranslateY(-mMapZero.getY());
        }
        for (Node s : mRoutingPane.getChildren()) {
            s.setTranslateX(-mMapZero.getX());
            s.setTranslateY(-mMapZero.getY());
        }
        for (Node s : mRoutingNodePane.getChildren()) {
            s.setTranslateX(-mMapZero.getX());
            s.setTranslateY(-mMapZero.getY());
        }
        for (Node s : mLabelPane.getChildren()) {
            s.setTranslateX(-mMapZero.getX());
            s.setTranslateY(-mMapZero.getY());
        }

        for (Iterator<Pane> it = mTransformPanes.stream().iterator(); it.hasNext(); ) {
            Pane p = it.next();
            for (Node s : p.getChildren()) {
                s.setTranslateX(-mMapZero.getX());
                s.setTranslateY(-mMapZero.getY());
            }
        }
        updateFakeShapes();
    }

    private Point2D calcNodePanePos(Point2D nodePos, Point2D paneZeroPos) {
        mCalcPoint.setCenterY(nodePos.getY());
        mCalcPoint.setCenterX(nodePos.getX());
        mCalcPoint.setTranslateX(-mMapZero.getX());
        mCalcPoint.setTranslateY(-mMapZero.getY());
        Point2D cPos = mCalcPoint.localToScreen(mCalcPoint.getCenterX(), mCalcPoint.getCenterY());
        Point2D cPosNode = new Point2D(cPos.getX() - paneZeroPos.getX(), cPos.getY() - paneZeroPos.getY());
        return new Point2D(cPosNode.getX() + mMapZero.getX(), cPosNode.getY() + mMapZero.getY());
    }

    private double getPrefetchBoxMargin() {
        return PREFETCH_MARGIN_PIXEL;
    }

    private Point2D coordinateToDisplay(double lon, double lat, int zoom) {
        double numberOfTiles = Math.pow(2, zoom);
        // LonToX
        double x = (lon + 180) * (numberOfTiles * GISUtils.TILESIZE) / 360.;
        // LatToY
        double projection = Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2));
        double y = (projection / Math.PI);
        y = 1 - y;
        y = y / 2 * (numberOfTiles * GISUtils.TILESIZE);
        return new Point2D(x, y);
    }

    private Point2D coordinateToDisplay(JsonArray coord, int zoom) {
        return coordinateToDisplay(coord.getDouble(0), coord.getDouble(1), zoom);
    }

    private Point2D coordinateToDisplay(Point2D coord, int zoom) {
        return coordinateToDisplay(coord.getX(), coord.getY(), zoom);
    }

    Point2D displayToCoordinate(double x, double y, int zoom) {
        // longitude
        double longitude = (x * (360 / (Math.pow(2, zoom) * GISUtils.TILESIZE))) - 180;
        // latitude
        double latitude = y * (2 / (Math.pow(2, zoom) * GISUtils.TILESIZE));
        latitude = 1 - latitude;
        latitude = latitude * Math.PI;
        latitude = Math.toDegrees(Math.atan(Math.sinh(latitude)));

        return new Point2D(longitude, latitude);
    }

    public Polyline displayCoordsPolyline(long osmId, JsonArray coords) {
        OSMPolyline polyline = new OSMPolyline(osmId);
        Double[] points = new Double[coords.size() * 2];
        int j = 0;
        for (int i = 0; i < coords.size(); i++) {
            JsonArray coord = (JsonArray) coords.get(i);
            Point2D pos = coordinateToDisplay(coord, mMapZoom);
            points[j] = pos.getX();
            points[j + 1] = pos.getY();
            j += 2;
        }
        polyline.getPoints().addAll(points);
        polyline.setTranslateX(-mMapZero.getX());
        polyline.setTranslateY(-mMapZero.getY());
        return polyline;
    }

    public Polyline displayCoordsPolyline(JsonArray coords) {
        Polyline polyline = new Polyline();
        Double[] points = new Double[coords.size() * 2];
        int j = 0;
        for (int i = 0; i < coords.size(); i++) {
            JsonArray coord = (JsonArray) coords.get(i);
            Point2D pos = coordinateToDisplay(coord, mMapZoom);
            points[j] = pos.getX();
            points[j + 1] = pos.getY();
            j += 2;
        }
        polyline.getPoints().addAll(points);
        polyline.setTranslateX(-mMapZero.getX());
        polyline.setTranslateY(-mMapZero.getY());
        return polyline;
    }

    public Polyline clonePolyline(long osmId, Polyline p) {
        OSMPolyline polyline = new OSMPolyline(osmId);
        polyline.getPoints().addAll(p.getPoints());
        polyline.setTranslateX(-mMapZero.getX());
        polyline.setTranslateY(-mMapZero.getY());
        return polyline;
    }

    public Polygon displayCoordsPolygon(long osmId, int areaType, JsonArray coords) {
        OSMPolygon polygon = new OSMPolygon(osmId, areaType);
        Double[] points = new Double[coords.size() * 2];
        int j = 0;
        for (int i = 0; i < coords.size(); i++) {
            JsonArray coord = (JsonArray) coords.get(i);
            Point2D pos = coordinateToDisplay(coord, mMapZoom);
            points[j] = pos.getX();
            points[j + 1] = pos.getY();
            j += 2;
        }
        polygon.getPoints().addAll(points);
        polygon.setTranslateX(-mMapZero.getX());
        polygon.setTranslateY(-mMapZero.getY());
        return polygon;
    }


    private void centerMapOnCoordinates(double lon, double lat) {
        mCenter = new Point2D(lon, lat);
        calcMapCenterPos();
        calcMapZeroPos(false);
        maybeLoadMapData(() -> showShapeAtCenter());
    }

    private void centerMapOnDisplay(Point2D pos) {
        mCenterPos = pos;
        calcCenterCoord();
        calcMapZeroPos(false);
        loadMapData(null);
    }

    private void centerMapOnCoordinatesWithZoom(double lon, double lat, int zoom) {
        mMapZoom = zoom;
        zoomLabel.setText(String.valueOf(mMapZoom));
        centerMapOnCoordinates(lon, lat);
    }

    private void centerMapOnDisplayWithZoom(Point2D pos, int zoom) {
        mMapZoom = zoom;
        zoomLabel.setText(String.valueOf(mMapZoom));
        centerMapOnDisplay(pos);
    }

    private void calcMapCenterPos() {
        mCenterPos = coordinateToDisplay(mCenter.getX(), mCenter.getY(), mMapZoom);
    }

    private void calcMapZeroPos(boolean sceneChanged) {
        if (sceneChanged) {
            mRotate.setPivotY(mScene.getHeight() / 2);
            mZRotate.setPivotY(mScene.getHeight() / 2);
            mZRotate.setPivotX(mScene.getWidth() / 2);
            setTransforms();
        }

        mMapZero = new Point2D(mCenterPos.getX() - mScene.getWidth() / 2,
                mCenterPos.getY() - mScene.getHeight() / 2 + topPane.getHeight());

        if (mTrackMode || mTrackReplayMode) {
            calcGPSPos();
        }
    }

    private void calcCenterCoord() {
        mCenter = displayToCoordinate(mCenterPos.getX(), mCenterPos.getY(), mMapZoom);
    }

    private Point2D getCoordOfPos(Point2D mousePos) {
        return displayToCoordinate(mMapZero.getX() + mousePos.getX(), mMapZero.getY() + mousePos.getY(), mMapZoom);
    }

    private void moveMap(double stepX, double stepY) {
        //long t = System.currentTimeMillis();
        double posX = mCenterPos.getX() - mMapZero.getX() + stepX;
        double posY = mCenterPos.getY() - mMapZero.getY() + stepY;

        mCenterPos = new Point2D(mMapZero.getX() + posX, mMapZero.getY() + posY);

        calcCenterCoord();
        calcMapZeroPos(false);
        maybeLoadMapData(() -> {
            infoLabel.setText("");
            posLabel.setText("");
        });
        //LogUtils.log("moveMap " + (System.currentTimeMillis() - t));
    }

    private List<Double> createBBoxAroundPoint(double lon, double lat, double margin) {
        List<Double> bbox = new ArrayList<>();
        double latRangeMax = lat + margin;
        double lonRangeMax = lon + margin * 1.4;
        double latRangeMin = lat - margin;
        double lonRangeMin = lon - margin * 1.4;
        Collections.addAll(bbox, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax);
        return bbox;
    }

    private BoundingBox getVisibleBBox() {
        if (isShow3DActive()) {
            return new BoundingBox(mMapZero.getX() - mScene.getWidth(), mMapZero.getY() - mScene.getHeight(),
                    mScene.getWidth() * 3, mScene.getHeight() * 3);
        } else {
            return new BoundingBox(mMapZero.getX(), mMapZero.getY(), mScene.getWidth(), mScene.getHeight());
        }
    }

    private BoundingBox getVisibleBBoxWithMargin(BoundingBox bbox) {
        double margin = getPrefetchBoxMargin();
        return new BoundingBox(bbox.getMinX() - margin, bbox.getMinY() - margin,
                bbox.getWidth() + 2 * margin, bbox.getHeight() + 2 * margin);
    }

    private List<Double> getBBoxInDeg(BoundingBox bbox) {
        Point2D pos1 = displayToCoordinate(bbox.getMinX(), bbox.getMinY(), mMapZoom);
        Point2D pos2 = displayToCoordinate(bbox.getMaxX(), bbox.getMaxY(), mMapZoom);

        List<Double> l = new ArrayList<>();
        Collections.addAll(l, pos1.getX(), pos1.getY(), pos2.getX(), pos2.getY());
        return l;
    }

    public int getZoom() {
        return mMapZoom;
    }

    private OSMShape findShapeAtPoint(Point2D pos, Set<Integer> areaTypes, int layer, Map<
            Integer, List<Node>> polylines) {
        List<Node> polyList = polylines.get(layer);
        // again reverse by layer order
        for (int i = polyList.size() - 1; i >= 0; i--) {
            Node s = polyList.get(i);
            if (s instanceof OSMPolygon) {
                if (areaTypes.size() == 0 || areaTypes.contains(((OSMPolygon) s).getAreaType())) {
                    if (s.contains(pos)) {
                        OSMPolygon polygon = new OSMPolygon((OSMPolygon) s);
                        polygon.getPoints().addAll(((OSMPolygon) s).getPoints());
                        polygon.setTranslateX(-mMapZero.getX());
                        polygon.setTranslateY(-mMapZero.getY());
                        return polygon;
                    }
                }
            } else if (s instanceof OSMPolyline) {
                if (s.contains(pos)) {
                    OSMPolyline polyline = new OSMPolyline((OSMPolyline) s);
                    polyline.getPoints().addAll(((OSMPolyline) s).getPoints());
                    polyline.setTranslateX(-mMapZero.getX());
                    polyline.setTranslateY(-mMapZero.getY());
                    return polyline;
                }
            } else if (s instanceof ImageView) {
                // POI images
                if (s.contains(pos)) {
                    return null;
                }
            }
        }
        return null;
    }

    // returns a copy
    private OSMShape findShapeAtPoint(Point2D pos, Set<Integer> areaTypes) {
        // from top layer down
        List<Integer> keyList = new ArrayList<>();
        keyList.addAll(mWayPolylines.keySet());
        Collections.reverse(keyList);

        for (int layer : keyList) {
            OSMShape shape = findShapeAtPoint(pos, areaTypes, layer, mWayPolylines);
            if (shape != null) {
                return shape;
            }
            shape = findShapeAtPoint(pos, areaTypes, layer, mAreaPolylines);
            if (shape != null) {
                return shape;
            }
        }
        return null;
    }

    private OSMPolyline findWayAtPoint(Point2D pos) {
        JsonArray edgeList = QueryController.getInstance().getEdgeOnPos(pos.getX(), pos.getY(), 0.0005, 30, 20);
        if (edgeList.size() != 0) {
            JsonObject edge = (JsonObject) edgeList.get(0);
            long osmId = (long) edge.get("wayId");
            OSMPolyline shape = findWayOfOSMId(osmId);
            if (shape != null) {
                return shape;
            }
        }
        return null;
    }

    private OSMShape findShapeOfOSMId(long osmId, Map<Integer, List<Node>> polylines) {
        for (List<Node> polyList : polylines.values()) {
            for (Node s : polyList) {
                if (s instanceof OSMPolygon) {
                    if (((OSMPolygon) s).getOSMId() == osmId) {
                        OSMPolygon polygon = new OSMPolygon((OSMPolygon) s);
                        polygon.getPoints().addAll(((OSMPolygon) s).getPoints());
                        polygon.setTranslateX(-mMapZero.getX());
                        polygon.setTranslateY(-mMapZero.getY());
                        return polygon;
                    }
                } else if (s instanceof OSMPolyline) {
                    if (((OSMPolyline) s).getOSMId() == osmId) {
                        OSMPolyline polyline = new OSMPolyline((OSMPolyline) s);
                        polyline.getPoints().addAll(((OSMPolyline) s).getPoints());
                        polyline.setTranslateX(-mMapZero.getX());
                        polyline.setTranslateY(-mMapZero.getY());
                        return polyline;
                    }
                }
            }
        }
        return null;
    }

    private OSMShape findShapeOfOSMId(long osmId) {
        OSMShape shape = findShapeOfOSMId(osmId, mWayPolylines);
        if (shape != null) {
            return shape;
        }
        shape = findShapeOfOSMId(osmId, mAreaPolylines);
        if (shape != null) {
            return shape;
        }
        return null;
    }

    private OSMPolyline findWayOfOSMId(long osmId) {
        for (List<Node> polyList : mWayPolylines.values()) {
            for (Node s : polyList) {
                if (s instanceof OSMPolyline) {
                    if (((OSMPolyline) s).getOSMId() == osmId) {
                        OSMPolyline polyline = new OSMPolyline((OSMPolyline) s);
                        polyline.getPoints().addAll(((OSMPolyline) s).getPoints());
                        polyline.setTranslateX(-mMapZero.getX());
                        polyline.setTranslateY(-mMapZero.getY());
                        return polyline;
                    }
                }
            }
        }
        return null;
    }

    private void updateGPSPos(JsonObject gpsData, boolean force) {
        double lat = ((BigDecimal) gpsData.get("lat")).doubleValue();
        double lon = ((BigDecimal) gpsData.get("lon")).doubleValue();
        if (lat == -1 || lon == -1) {
            return;
        }
        boolean hasMoved = false;

        if (mGPSData != null) {
            if (lat != ((BigDecimal) mGPSData.get("lat")).doubleValue() || lon != ((BigDecimal) mGPSData.get("lon")).doubleValue()) {
                int speed = ((BigDecimal) gpsData.get("speed")).intValue();
                if (speed > 1) {
                    hasMoved = true;
                }
            }
        } else {
            hasMoved = true;
        }

        if ((force || hasMoved) && (mTrackMode || mTrackReplayMode)) {
            mGPSData = gpsData;
            if (!mTrackReplayMode) {
                GPSUtils.addGPSData(mGPSData);
            }
            mGPSPos = new Point2D(lon, lat);
            moveToGPSPos();
        }
    }

    private void moveToGPSPos() {
        if (mGPSData == null) {
            return;
        }
        //LogUtils.log("moveToGPSPos " + mGPSPos);
        mCenter = mGPSPos;
        int bearing = ((BigDecimal) mGPSData.get("bearing")).intValue();
        if (bearing != -1) {
            mZRotate.setAngle(360 - bearing);
            mGPSDot.setRotate(bearing);
        }

        posLabel.setText(String.format("%.5f:%.5f", mCenter.getX(), mCenter.getY()));
        int speed = ((BigDecimal) mGPSData.get("speed")).intValue();
        speedLabel.setText((int) (speed * 3.6) + "km/h");
        int alt = ((BigDecimal) mGPSData.get("altitude")).intValue();
        altLabel.setText(alt + "m");

        calcMapCenterPos();
        calcMapZeroPos(false);
        maybeLoadMapData(null);

        Task<Void> findEdge = new Task() {
            @Override
            protected Object call() throws Exception {
                boolean foundEdge = false;
                long t = System.currentTimeMillis();


                if (mCurrentEdge == null) {
                    // #1 if we know nothing just pick the closest edge we can find in an area
                    // closest point of the edge with max 30m away
                    LogUtils.log(LogUtils.TAG_TRACKING, "search nearest edge");
                    JsonObject edge = getClosestEdgeOnPos(mGPSPos);
                    if (edge != null) {
                        LogUtils.log(LogUtils.TAG_TRACKING, "use minimal distance edge " + edge);
                        mCurrentEdge = edge;
                        mLastUsedEdge = mCurrentEdge;
                        foundEdge = true;

                        // find out where we are going and set mNextRefId
                        calcApproachingRef(bearing);
                        JsonArray nextEdgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(mNextRefId, -1);
                        calPredicationWays(nextEdgeList);
                    }
                } else {
                    // #2 we caclulated mNextEdgeList in calPredicationWays in the last run
                    // use that to filter edges that are around the current position as possible
                    // next edges
                    List<Double> bbox = createBBoxAroundPoint(mGPSPos.getX(), mGPSPos.getY(), 0.00008);
                    Map<Long, JsonObject> edgeMap = QueryController.getInstance().getEdgesAroundPointWithGeom(bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));

                    LogUtils.log(LogUtils.TAG_TRACKING, "getEdgesAroundPointWithGeom = " + edgeMap.keySet());
                    boolean searchNextEdge = true;
                    if (mCurrentEdge != null) {
                        long currentEdgeId = (long) mCurrentEdge.get("id");
                        if (edgeMap.containsKey(currentEdgeId)) {
                            LogUtils.log(LogUtils.TAG_TRACKING, "prefer current edge");
                            searchNextEdge = false;
                            foundEdge = true;

                            // find out where we are going and set mNextRefId
                            if (mNextRefId == -1) {
                                calcApproachingRef(bearing);
                                JsonArray nextEdgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(mNextRefId, -1);
                                calPredicationWays(nextEdgeList);
                            }
                        }

                        if (searchNextEdge) {
                            JsonObject nextEdge = null;
                            boolean foundNext = false;

                            // first check if any edge that is in mNextEdgeList
                            // has fallen outside of edgeMap - if yes remove that one from mNextEdgeList
                            JsonArray nextEdgeList = new JsonArray();
                            for (int i = 0; i < mNextEdgeList.size(); i++) {
                                JsonObject edge = (JsonObject) mNextEdgeList.get(i);
                                long edgeId = (long) edge.get("id");
                                if (edgeMap.containsKey(edgeId)) {
                                    nextEdgeList.add(edge);
                                } else {
                                    LogUtils.log(LogUtils.TAG_TRACKING, "remove prediction edge outside if possible edges " + edgeId);
                                }
                            }
                            mNextEdgeList = nextEdgeList;

                            // calculate heading of all edges in mNextEdgeList based on mNextRefId
                            // if there are edges that have a diff of heading vs. current bearing  < 15 deg
                            // add as possible next edges
                            JsonArray headingEdges = getNextEdgeWithBestHeading(bearing);
                            long headingEdgeId = -1;
                            JsonObject headingEdge = null;
                            if (headingEdges.size() > 1) {
                                // we have more then one edge with a close enough heading
                                // instead of continue and maybe pick the wrong cancel this run
                                // and wait for the next position and start with #1
                                LogUtils.log(LogUtils.TAG_TRACKING, "delay because multiple best heading matching edges: " + headingEdges.size());
                                // delay to resolve from pos in next round
                                /*headingEdge = getClosestEdge(mGPSPos.getX(), mGPSPos.getY(), headingEdges, 30);
                                if (headingEdge != null) {
                                    headingEdgeId = (long) headingEdge.get("edgeId");
                                    System.out.println("picked heading matching edge: " + headingEdgeId);
                                }*/
                                mCurrentEdge = null;
                                return null;
                            } else if (headingEdges.size() == 1) {
                                // we have exactly once edge with a close enough heading
                                // remember that for later
                                headingEdge = (JsonObject) headingEdges.get(0);
                                headingEdgeId = (long) headingEdge.get("id");
                                nextEdge = headingEdge;
                                foundNext = true;
                                LogUtils.log(LogUtils.TAG_TRACKING, "one best heading matching edge: " + headingEdgeId);
                            }
                            // filter out mNextEdgeList which edges are still in area
                            // the edges in mNextEdgeList are sorted with the best matching one
                            // on position 0 so try that one first
                            /*for (JsonObject edge : edgeMap.values()) {
                                long edgeId = (long) edge.get("id");
                                if (edgeId == currentEdgeId) {
                                    continue;
                                }

                                if (mNextEdgeList.size() > 0) {
                                    JsonObject firstEdge = (JsonObject) mNextEdgeList.get(0);
                                    long firstEdgeId = (long) firstEdge.get("edgeId");
                                    if (firstEdgeId == edgeId) {
                                        System.out.println("found matching first edge: " + firstEdge);
                                        foundNext = true;
                                        nextEdge = firstEdge;
                                        break;
                                    }
                                }
                            }
                            if (!foundNext) {
                                // if needed try all the other edges
                                for (JsonObject edge : edgeMap.values()) {
                                    long edgeId = (long) edge.get("id");
                                    if (edgeId == currentEdgeId) {
                                        continue;
                                    }

                                    if (mNextEdgeList.size() > 0) {
                                        for (int j = 1; j < mNextEdgeList.size(); j++) {
                                            JsonObject possibleEdge = (JsonObject) mNextEdgeList.get(j);
                                            long possibleEdgeId = (long) possibleEdge.get("id");
                                            if (possibleEdgeId == edgeId) {
                                                System.out.println("found matching next edge: " + possibleEdge);
                                                foundNext = true;
                                                nextEdge = possibleEdge;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }*/
                            if (foundNext && nextEdge != null) {
                                // but a edge with better heading will overwrite all of this above
                                long nextEdgeId = (long) nextEdge.get("id");
                                if (headingEdgeId != -1) {
                                    if (headingEdgeId != nextEdgeId) {
                                        LogUtils.log(LogUtils.TAG_TRACKING, "headingEdgeId = " + headingEdgeId + " nextEdgeId = " + nextEdgeId);
                                        nextEdge = headingEdge;
                                    } else {
                                        LogUtils.log(LogUtils.TAG_TRACKING, "best heading edge picked");
                                    }
                                }
                                long nextStartRef = (long) nextEdge.get("startRef");
                                long nextEndRef = (long) nextEdge.get("endRef");

                                if (mCurrentEdge != null) {
                                    long currStartRef = (long) mCurrentEdge.get("startRef");
                                    long currEndRef = (long) mCurrentEdge.get("endRef");

                                    if (nextStartRef == currEndRef || nextStartRef == currStartRef) {
                                        mNextRefId = nextEndRef;
                                    } else if (nextEndRef == currEndRef || nextEndRef == currStartRef) {
                                        mNextRefId = nextStartRef;
                                    }
                                }
                                mCurrentEdge = nextEdge;
                                mLastUsedEdge = mCurrentEdge;
                                foundEdge = true;

                                JsonArray edgeList = null;
                                if (mNextRefId != -1) {
                                    edgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(mNextRefId, -1);
                                } else {
                                    edgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(nextStartRef, nextEndRef);
                                }
                                calPredicationWays(edgeList);
                            }
                        }
                    }
                }

                if (!foundEdge || mCurrentEdge == null) {
                    LogUtils.log(LogUtils.TAG_TRACKING, "no matching next edge found");
                    mCurrentEdge = null;
                } else {
                    if (mCurrentEdge != null) {
                        long wayId = (long) mCurrentEdge.get("wayId");
                        OSMPolyline shape = findWayOfOSMId(wayId);
                        if (shape != null) {
                            mTrackingOSMId = wayId;
                            mTrackingShape = shape;
                            mTrackingShape.setTracking();

                            JsonObject way = mOSMObjects.get(mTrackingOSMId);
                            if (way != null) {
                                JsonObject tags = (JsonObject) way.get("tags");
                                if (tags != null) {
                                    String name = (String) tags.get("name");
                                    if (name != null) {
                                        Platform.runLater(() -> {
                                            wayLabel.setText(name);
                                        });
                                    }
                                }
                            }
                        }
                    }
                }

                return null;
            }
        };

        mExecutorService.submit(findEdge);
        //new Thread(findEdge).start();
    }

    private void calPredicationWays(JsonArray nextEdgeList) {
        Objects.requireNonNull(mCurrentEdge, "calPredicationWays");

        mPredictionWays.clear();
        mNextEdgeList = new JsonArray();

        LinkedHashMap<Integer, JsonArray> predictionMap = new LinkedHashMap<>();
        long currentEdgeId = (long) mCurrentEdge.get("id");
        int currentStreetInfo = (int) mCurrentEdge.get("streetInfo");
        int currentStreetTypeId = currentStreetInfo & 15;

        for (int j = 0; j < nextEdgeList.size(); j++) {
            JsonObject nextEdge = (JsonObject) nextEdgeList.get(j);
            long predictionWayId = (long) nextEdge.get("wayId");
            long predictionEdgeId = (long) nextEdge.get("id");
            if (predictionEdgeId == currentEdgeId) {
                continue;
            }
            JsonObject way = mOSMObjects.get(predictionWayId);
            if (way != null) {
                int quality = 0;
                int streetInfo = (int) way.get("streetInfo");
                int streetTypeId = streetInfo & 15;
                int oneway = (streetInfo & 63) >> 4;
                int roundabout = (streetInfo & 127) >> 6;

                if (mNextRefId != -1) {
                    if (oneway != 0) {
                        if (!OSMUtils.isValidOnewayEnter(oneway, mNextRefId, nextEdge)) {
                            LogUtils.log(LogUtils.TAG_TRACKING, "forbidden oneway enter " + nextEdge);
                            continue;
                        }
                    }
                    if (roundabout != 0) {
                        long startRef = (long) nextEdge.get("startRef");
                        if (mNextRefId != startRef) {
                            LogUtils.log(LogUtils.TAG_TRACKING, "forbidden roundabout left turn " + nextEdge);
                            continue;
                        }
                    }
                }
                if (streetTypeId == currentStreetTypeId) {
                    quality += 10;
                }
                if (predictionMap.containsKey(quality)) {
                    predictionMap.get(quality).add(nextEdge);
                } else {
                    JsonArray edgeList = new JsonArray();
                    edgeList.add(nextEdge);
                    predictionMap.put(quality, edgeList);
                }
                mPredictionWays.add((long) nextEdge.get("wayId"));
            }
        }
        ArrayList<Integer> qualityList = new ArrayList<>(predictionMap.keySet());
        Collections.sort(qualityList);

        LogUtils.log(LogUtils.TAG_TRACKING, "calPredicationWays currentEdge = " + currentEdgeId + " streetTypeId= " + currentStreetTypeId + " qualityList = " + qualityList);

        for (int i = qualityList.size() - 1; i >= 0; i--) {
            JsonArray edgeList = predictionMap.get(qualityList.get(i));
            for (int j = 0; j < edgeList.size(); j++) {
                JsonObject nextEdge = (JsonObject) edgeList.get(j);
                int streetInfo = (int) nextEdge.get("streetInfo");
                int streetTypeId = streetInfo & 15;
                mNextEdgeList.add(nextEdge);
                LogUtils.log(LogUtils.TAG_TRACKING, "calPredicationWays nextEdge = " + nextEdge + " streetTypeId = " + streetTypeId);
            }
        }
    }

    private boolean isPositionVisible(Point2D mapPos) {
        return mFetchBBox.contains(mapPos);
    }

    private void addGPSDot() {
        mGPSDot.setStroke(mTrackReplayMode ? Color.RED : Color.BLACK);
        mTrackingPane.getChildren().add(mGPSDot);
    }

    private void calcGPSPos() {
        mMapGPSPos = coordinateToDisplay(mGPSPos.getX(), mGPSPos.getY(), mMapZoom);
        mGPSDot.setCenterX(mMapGPSPos.getX() - mMapZero.getX());
        mGPSDot.setCenterY(mMapGPSPos.getY() - mMapZero.getY());
    }

    @Override
    public void onLocation(JsonObject gpsData) {
        Platform.runLater(() -> updateGPSPos(gpsData, false));
    }

    @Override
    public void onLocation(JsonObject gpsData, boolean force) {
        Platform.runLater(() -> updateGPSPos(gpsData, force));
    }

    private void updateTrackMode() {
        if (mTrackMode) {
            mGPSData = null;
            mGPSThread = new GPSThread();
            if (!mGPSThread.startThread(MainController.this)) {
                LogUtils.error("open port " + GPSThread.DEV_TTY_ACM_0 + " failed");
                mMapGPSPos = new Point2D(0, 0);
            } else {
                try {
                    GPSUtils.startTrackLog();
                } catch (IOException e) {
                    LogUtils.error("start GPS tracker failed", e);
                }
            }
        } else {
            if (mGPSThread != null) {
                stopGPSTracking();
                mMapGPSPos = new Point2D(0, 0);
                mGPSData = null;
                mZRotate.setAngle(0);
                drawShapes();
            }
        }
        trackModeButton.setGraphic(new ImageView(new Image(mTrackMode ? "/images/ui/gps.png" : "/images/ui/gps-circle.png")));
    }

    private void stopReplay() {
        if (mTrackReplayThread != null) {
            mTrackReplayMode = false;
            mTrackReplayThread.stopThread();
            try {
                mTrackReplayThread.join();
            } catch (InterruptedException e) {
            }
            mTrackReplayThread = null;
        }
        posLabel.setText("");
        speedLabel.setText("");
        altLabel.setText("");
        wayLabel.setText("");
    }

    private void stopGPSTracking() {
        if (mGPSThread != null) {
            GPSUtils.stopTrackLog();
            mTrackMode = false;
            mGPSThread.stopThread();
            try {
                mGPSThread.join();
            } catch (InterruptedException e) {
            }
        }
        posLabel.setText("");
        speedLabel.setText("");
        altLabel.setText("");
        wayLabel.setText("");
    }

    private void setTransforms() {
        if (isShow3DActive()) {
            for (Iterator<Pane> it = mTransformPanes.stream().iterator(); it.hasNext(); ) {
                Pane p = it.next();
                p.getTransforms().clear();
                p.getTransforms().add(mRotate);
                p.getTransforms().add(mZRotate);
            }

            mRoutingPane.getTransforms().clear();
            mRoutingPane.getTransforms().add(mRotate);
            mRoutingPane.getTransforms().add(mZRotate);

            mTrackingPane.getTransforms().clear();
            mTrackingPane.getTransforms().add(mRotate);
            mTrackingPane.getTransforms().add(mZRotate);
        } else {
            for (Iterator<Pane> it = mTransformPanes.stream().iterator(); it.hasNext(); ) {
                Pane p = it.next();
                p.getTransforms().clear();
                p.getTransforms().add(mZRotate);
            }

            mRoutingPane.getTransforms().clear();
            mRoutingPane.getTransforms().add(mZRotate);

            mTrackingPane.getTransforms().clear();
            mTrackingPane.getTransforms().add(mZRotate);
        }
    }

    private JsonArray getNextEdgeWithBestHeading(int bearing) {
        Objects.requireNonNull(mCurrentEdge, "getNextEdgeWithBestHeading");
        long currentEdgeId = (long) mCurrentEdge.get("id");
        JsonArray bestEdgeList = new JsonArray();
        for (int i = 0; i < mNextEdgeList.size(); i++) {
            JsonObject edge = (JsonObject) mNextEdgeList.get(i);
            long edgeId = (long) edge.get("id");
            if (edgeId == currentEdgeId) {
                continue;
            }
            JsonArray coords = (JsonArray) edge.get("coords");
            JsonArray pos0 = null;
            JsonArray pos1 = null;
            int diff1 = 360;
            int diff2 = 360;

            if (mNextRefId != -1) {
                long startRef = (long) edge.get("startRef");
                long endRef = (long) edge.get("endRef");
                if (mNextRefId == startRef) {
                    pos0 = (JsonArray) coords.get(0);
                    pos1 = (JsonArray) coords.get(1);
                } else if (mNextRefId == endRef) {
                    pos0 = (JsonArray) coords.get(coords.size() - 1);
                    pos1 = (JsonArray) coords.get(coords.size() - 2);
                }

                int heading = GISUtils.headingDegrees((double) pos0.get(0), (double) pos0.get(1), (double) pos1.get(0), (double) pos1.get(1));
                diff1 = GISUtils.headingDiffAbsolute(bearing, heading);
            } else {
                pos0 = (JsonArray) coords.get(0);
                pos1 = (JsonArray) coords.get(coords.size() - 1);

                int heading1 = GISUtils.headingDegrees((double) pos0.get(0), (double) pos0.get(1), (double) pos1.get(0), (double) pos1.get(1));
                int heading2 = GISUtils.headingDegrees((double) pos1.get(0), (double) pos1.get(1), (double) pos0.get(0), (double) pos0.get(1));
                diff1 = GISUtils.headingDiffAbsolute(bearing, heading1);
                diff2 = GISUtils.headingDiffAbsolute(bearing, heading2);
            }

            LogUtils.log(LogUtils.TAG_TRACKING, "getNextEdgeWithBestHeading " + diff1 + " " + diff2);

            if (diff1 < 30 || diff2 < 30) {
                bestEdgeList.add(edge);
            }
        }
        return bestEdgeList;
    }

    private void resetTracking() {
        mCurrentEdge = null;
        mNextRefId = -1;
        mLastUsedEdge = null;
        if (mNextEdgeList != null) {
            mNextEdgeList.clear();
        }
        if (mPredictionWays != null) {
            mPredictionWays.clear();
        }
        mTrackingShape = null;
    }

    // find out which way we are going on mCurrentEdge
    // based on current pos and bearing and heading to first and last pos of this edge
    private void calcApproachingRef(int bearing) {
        Objects.requireNonNull(mCurrentEdge, "calcApproachingRef");
        long currStartRef = (long) mCurrentEdge.get("startRef");
        long currEndRef = (long) mCurrentEdge.get("endRef");

        JsonArray coords = (JsonArray) mCurrentEdge.get("coords");
        JsonArray pos0 = (JsonArray) coords.get(0);
        JsonArray pos1 = (JsonArray) coords.get(coords.size() - 1);

        int heading1 = GISUtils.headingDegrees(mGPSPos.getX(), mGPSPos.getY(), (double) pos0.get(0), (double) pos0.get(1));
        int heading2 = GISUtils.headingDegrees(mGPSPos.getX(), mGPSPos.getY(), (double) pos1.get(0), (double) pos1.get(1));

        int diff1 = GISUtils.headingDiffAbsolute(bearing, heading1);
        int diff2 = GISUtils.headingDiffAbsolute(bearing, heading2);

        if (diff1 < diff2) {
            mNextRefId = currStartRef;
        } else {
            mNextRefId = currEndRef;
        }
        LogUtils.log(LogUtils.TAG_TRACKING, "calcApproachingRef = " + mNextRefId);
    }

    private boolean isShow3DActive() {
        return /*mMapZoom >= 14 &&*/ mShow3D;
    }

    private void addRoutingNode(RoutingNode.TYPE type, Point2D coordsPos, long edgeId, long wayId, long osmId) {
        RoutingNode routingNode = new RoutingNode(type, coordsPos, edgeId, wayId, osmId);
        mRoutingNodes.add(routingNode);
        storeRoutingNodes();

        if (edgeId != 0) {
            ResolvePositionTask task = new ResolvePositionTask(routingNode.getCoordsPos(), routingNode.getWayId(), routingNode.getOsmId());
            task.setOnSucceeded((succeededEvent) -> {
                String name = null;
                JsonObject way = task.getResolvedWay();
                JsonObject area = task.getResolvedArea();
                if (area != null) {
                    name = (String) area.get("name");
                }
                if (name == null && way != null) {
                    name = (String) way.get("name");
                }
                routingNode.setName(name);
                updateRouteNodeListContent();
                drawShapes();
            });
            mExecutorService.submit(task);
        }
    }

    private void addPinNode(RoutingNode.TYPE type, Point2D coordsPos, long edgeId, long wayId, long osmId) {
        RoutingNode routingNode = new RoutingNode(type, coordsPos, edgeId, wayId, osmId);
        mSavedNodes.add(routingNode);
        storeSavedNodes();

        if (edgeId != 0) {
            ResolvePositionTask task = new ResolvePositionTask(routingNode.getCoordsPos(), routingNode.getWayId(), routingNode.getOsmId());
            task.setOnSucceeded((succeededEvent) -> {
                String name = null;
                JsonObject way = task.getResolvedWay();
                JsonObject area = task.getResolvedArea();
                if (area != null) {
                    name = (String) area.get("name");
                }
                if (name == null && way != null) {
                    name = (String) way.get("name");
                }
                routingNode.setName(name);
                updateSavedNodeListContent();
                drawShapes();
            });
            mExecutorService.submit(task);
        } else {
            updateSavedNodeListContent();
            drawShapes();
        }
    }

    public void removeCustomNode(RoutingNode node) {
        if (node.getType() == RoutingNode.TYPE.PIN) {
            mSavedNodes.remove(node);
            updateSavedNodeListContent();
            storeSavedNodes();
        } else {
            mRoutingNodes.remove(node);
            updateRouteNodeListContent();
            storeRoutingNodes();
        }
        drawShapes();
    }

    private void updateRoutingNode(RoutingNode routingNode, boolean updateSize) {
        if (updateSize) {
            int size = OSMStyle.getPoiSizeForZoom(mMapZoom, 48);
            routingNode.setFitHeight(size);
            routingNode.setFitWidth(size);
            routingNode.setPreserveRatio(true);
        }
        Point2D coordsPos = routingNode.getCoordsPos();
        Point2D nodePos = coordinateToDisplay(coordsPos.getX(), coordsPos.getY(), mMapZoom);
        Point2D paneZeroPos = mRoutingNodePane.localToScreen(0, 0);
        Point2D pos = calcNodePanePos(nodePos, paneZeroPos);

        routingNode.setX(pos.getX() - routingNode.getFitWidth() / 2);
        routingNode.setY(pos.getY() - routingNode.getFitHeight());
    }

    private void updatePOINode(OSMImageView node, boolean updateSize) {
        if (updateSize) {
            int size = OSMStyle.getPoiSizeForZoom(mMapZoom, 48);
            node.setFitHeight(size);
            node.setFitWidth(size);
            node.setPreserveRatio(true);
        }
        Point2D paneZeroPos = mNodePane.localToScreen(0, 0);
        Point2D nodePos = node.getPos();
        Point2D pos = calcNodePanePos(nodePos, paneZeroPos);

        node.setX(pos.getX() - node.getFitWidth() / 2);
        node.setY(pos.getY() - node.getFitHeight());
    }

    private void updateLabelShape(OSMTextLabel label) {
        Point2D paneZeroPos = mLabelPane.localToScreen(0, 0);
        Point2D nodePos = label.getPos();
        Point2D pos = calcNodePanePos(nodePos, paneZeroPos);
        label.setX(pos.getX() - label.getLayoutBounds().getWidth() / 2);
        label.setY(pos.getY() - label.getLayoutBounds().getHeight() / 2);
    }

    private void buildContextMenu() {
        mContextMenu.getItems().clear();
        MenuItem menuItem;

        RoutingNode node = getSelectedRoutingNode(mMouseClickedNodePos);
        if (node != null) {
            menuItem = new MenuItem(" Remove");
            menuItem.setOnAction(ev -> {
                removeCustomNode(node);
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        } else {
            if (!hasRoutingStart() || !hasRoutingEnd()) {
                menuItem = new MenuItem(hasRoutingStart() ? " Add finish" : " Add start");
                menuItem.setOnAction(ev -> {
                    // find closest edge
                    JsonObject edge = getClosestEdgeOnPos(mMouseClickedCoordsPos);
                    if (edge != null) {
                        mSelectedEdge = edge;
                        createSelectedEdgeShape();

                        Point2D pos = coordinateToDisplay(mMouseClickedCoordsPos, mMapZoom);
                        OSMShape shape = findShapeAtPoint(pos, OSMUtils.SELECT_AREA_TYPE);
                        long osmId = -1;
                        if (shape != null) {
                            osmId = shape.getOSMId();
                        }
                        LogUtils.log("addRoutingNode: use minimal distance edge = " + edge + " osmId = " + osmId);

                        addRoutingNode(hasRoutingStart() ? RoutingNode.TYPE.FINISH : RoutingNode.TYPE.START, mMouseClickedCoordsPos,
                                (long) mSelectedEdge.get("id"),
                                (long) mSelectedEdge.get("wayId"), osmId);
                    } else {
                        LogUtils.log("addRoutingNode: failed to resolve edge for position");
                    }
                });
                menuItem.setStyle("-fx-font-size: 20");
                mContextMenu.getItems().add(menuItem);
            }
        }
        /*if (isCalcRoutePossible()) {
            menuItem = new MenuItem(" Calc route ");
            menuItem.setOnAction(ev -> {
                calcRoute(getRoutingStart(), getRoutingEnd());
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        if (isCalcRoutePossible()) {
            menuItem = new MenuItem(" Revert route ");
            menuItem.setOnAction(ev -> {
                calcRoute(getRoutingEnd(), getRoutingStart());
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        if (isCalcRoutePossible()) {
            menuItem = new MenuItem(" Clear route ");
            menuItem.setOnAction(ev -> {
                clearRoute();
                drawShapes();
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }*/
        mContextMenu.getItems().add(new SeparatorMenuItem());
        menuItem = new MenuItem(" Remember location ");
        menuItem.setOnAction(ev -> {
            JsonObject edge = getClosestEdgeOnPos(mMouseClickedCoordsPos);
            Point2D pos = coordinateToDisplay(mMouseClickedCoordsPos, mMapZoom);
            OSMShape shape = findShapeAtPoint(pos, OSMUtils.SELECT_AREA_TYPE);
            long osmId = -1;
            if (shape != null) {
                osmId = shape.getOSMId();
            }

            addPinNode(RoutingNode.TYPE.PIN, mMouseClickedCoordsPos,
                    edge != null ? (long) edge.get("id") : 0,
                    edge != null ? (long) edge.get("wayId") : 0, osmId);
        });
        menuItem.setStyle("-fx-font-size: 20");
        mContextMenu.getItems().add(menuItem);

        mContextMenu.getItems().add(new SeparatorMenuItem());

        menuItem = new MenuItem(" Toggle 3D ");
        menuItem.setOnAction(ev -> {
            mShow3D = !mShow3D;
            calcMapCenterPos();
            calcMapZeroPos(true);
            maybeLoadMapData(null);
        });
        menuItem.setStyle("-fx-font-size: 20");
        mContextMenu.getItems().add(menuItem);

        mContextMenu.getItems().add(new SeparatorMenuItem());

        if (mLocationHistory.size() != 0 && mLocationHistoryIndex != 0) {
            menuItem = new MenuItem(" Back ");
            menuItem.setOnAction(ev -> {
                goBackInLocationHistory();
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        if (mLocationHistory.size() != 0 && mLocationHistoryIndex < mLocationHistory.size() - 1) {
            menuItem = new MenuItem(" Forward ");
            menuItem.setOnAction(ev -> {
                goForwardInLocationHistory();
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
    }

    private void buildButtonMenu() {
        mMenuButtonMenu.getItems().clear();
        MenuItem menuItem = new MenuItem(" Load track ");
        menuItem.setOnAction(ev -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Track file");
            File logDir = new File(System.getProperty("user.dir"), "logs");
            fileChooser.setInitialDirectory(logDir);
            mCurrentTrackFile = fileChooser.showOpenDialog(mPrimaryStage);
            if (mCurrentTrackFile != null) {
                // stop current first
                if (mTrackReplayThread != null) {
                    mTrackReplayMode = false;
                    mTrackReplayThread.stopThread();
                    try {
                        mTrackReplayThread.join();
                    } catch (InterruptedException e) {
                    }
                    mTrackReplayThread = null;
                }

                mTrackReplayThread = new TrackReplayThread();
                if (!mTrackReplayThread.setupReplay(mCurrentTrackFile, this)) {
                    LogUtils.error("failed to setup replay thread");
                    mTrackReplayThread = null;
                    mTrackReplayMode = false;
                } else {
                    borderPane.setBottom(bottomPane);
                    mTrackMode = false;
                    updateTrackMode();
                    mTrackReplayMode = true;
                    startReplayButton.setDisable(false);
                    stopReplayButton.setDisable(true);
                    pauseReplayButton.setDisable(true);
                    stepReplayButton.setDisable(true);
                    trackModeButton.setDisable(true);
                    resetTracking();
                }
            }
        });
        menuItem.setStyle("-fx-font-size: 20");
        mMenuButtonMenu.getItems().add(menuItem);
        menuItem = new MenuItem(" Clear track ");
        menuItem.setOnAction(ev -> {
            trackModeButton.setDisable(false);
            borderPane.setBottom(infoPane);
            stopReplay();
            resetTracking();
            mZRotate.setAngle(0);
            drawShapes();
        });
        menuItem.setStyle("-fx-font-size: 20");
        mMenuButtonMenu.getItems().add(menuItem);
    }

    RoutingNode getSelectedRoutingNode(Point2D nodePos) {
        for (RoutingNode node : mRoutingNodes) {
            if (node.contains(nodePos)) {
                return node;
            }
        }
        return null;
    }

    private void hideAllMenues() {
        if (mContextMenu.isShowing()) {
            mContextMenu.hide();
        }
        if (mMenuButtonMenu.isShowing()) {
            mMenuButtonMenu.hide();
        }
    }

    private void storeSavedNodes() {
        JsonArray nodes = new JsonArray();
        for (RoutingNode node : mSavedNodes) {
            nodes.add(node.toJson());
            LogUtils.log("saved node = " + node.toJson() + " edgeId = " + node.getEdgeId());
        }
        Config.getInstance().put("savedNodes", nodes);
        Config.getInstance().save();
    }

    private void storeRoutingNodes() {
        JsonArray nodes = new JsonArray();
        for (RoutingNode node : mRoutingNodes) {
            nodes.add(node.toJson());
            LogUtils.log("saved node = " + node.toJson() + " edgeId = " + node.getEdgeId());
        }
        Config.getInstance().put("routingNodes", nodes);
        Config.getInstance().save();
    }

    private void restoreSavedNodes() {
        JsonArray nodes = (JsonArray) Config.getInstance().get("savedNodes", new JsonArray());
        for (int i = 0; i < nodes.size(); i++) {
            RoutingNode node = new RoutingNode((JsonObject) nodes.get(i));
            if (node.getType() != RoutingNode.TYPE.PIN) {
                continue;
            }
            mSavedNodes.add(node);

            if (node.getWayId() != 0) {
                ResolvePositionTask task = new ResolvePositionTask(node.getCoordsPos(), node.getWayId(), node.getOsmId());
                task.setOnSucceeded((succeededEvent) -> {
                    LogUtils.log("restoreSavedNodes: ResolvePositionTask succeededEvent");
                    JsonObject edge = task.getResolvedEdge();
                    if (edge != null) {
                        node.setEdgeId((long) edge.get("id"));
                        LogUtils.log("resolved node = " + node.toJson() + " edgeId = " + node.getEdgeId());
                    }
                    String name = null;
                    JsonObject way = task.getResolvedWay();
                    JsonObject area = task.getResolvedArea();
                    if (area != null) {
                        name = (String) area.get("name");
                    }
                    if (name == null && way != null) {
                        name = (String) way.get("name");
                    }
                    node.setName(name);
                    updateSavedNodeListContent();
                });
                updateSavedNodeListContent();
                mExecutorService.submit(task);
            }
        }
    }

    public void restoreRoutingNode(JsonObject routingNode) {
        RoutingNode node = new RoutingNode(routingNode);
        if (node.getType() == RoutingNode.TYPE.FINISH && hasRoutingEnd()) {
            return;
        }
        if (node.getType() == RoutingNode.TYPE.START && hasRoutingStart()) {
            return;
        }

        mRoutingNodes.add(node);

        if (node.getWayId() != 0) {
            ResolvePositionTask task = new ResolvePositionTask(node.getCoordsPos(), node.getWayId(), node.getOsmId());
            task.setOnSucceeded((succeededEvent) -> {
                LogUtils.log("restoreRouteNode: ResolvePositionTask succeededEvent");
                JsonObject edge = task.getResolvedEdge();
                if (edge != null) {
                    node.setEdgeId((long) edge.get("id"));
                    LogUtils.log("resolved node = " + node.toJson() + " edgeId = " + node.getEdgeId());
                }
                String name = null;
                JsonObject way = task.getResolvedWay();
                JsonObject area = task.getResolvedArea();
                if (area != null) {
                    name = (String) area.get("name");
                }
                if (name == null && way != null) {
                    name = (String) way.get("name");
                }
                node.setName(name);
                updateRouteNodeListContent();
            });
            updateRouteNodeListContent();
            mExecutorService.submit(task);
        }
    }

    private void restoreRoutingNodes() {
        JsonArray nodes = (JsonArray) Config.getInstance().get("routingNodes", new JsonArray());
        for (int i = 0; i < nodes.size(); i++) {
            RoutingNode node = new RoutingNode((JsonObject) nodes.get(i));
            if (node.getType() != RoutingNode.TYPE.START && node.getType() != RoutingNode.TYPE.FINISH) {
                continue;
            }
            mRoutingNodes.add(node);

            if (node.getWayId() != 0) {
                ResolvePositionTask task = new ResolvePositionTask(node.getCoordsPos(), node.getWayId(), node.getOsmId());
                task.setOnSucceeded((succeededEvent) -> {
                    LogUtils.log("restoreRoutingNodes: ResolvePositionTask succeededEvent");
                    JsonObject edge = task.getResolvedEdge();
                    if (edge != null) {
                        node.setEdgeId((long) edge.get("id"));
                        LogUtils.log("resolved node = " + node.toJson() + " edgeId = " + node.getEdgeId());
                    }
                    String name = null;
                    JsonObject way = task.getResolvedWay();
                    JsonObject area = task.getResolvedArea();
                    if (area != null) {
                        name = (String) area.get("name");
                    }
                    if (name == null && way != null) {
                        name = (String) way.get("name");
                    }
                    node.setName(name);
                    updateRouteNodeListContent();
                });
                updateRouteNodeListContent();
                mExecutorService.submit(task);
            }
        }
    }

    private boolean hasRoutingStart() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.START).count() != 0;
    }

    private boolean hasRoutingStartResolved() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.START && routingNode.getEdgeId() != -1).count() != 0;
    }

    private RoutingNode getRoutingStart() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.START).findFirst().get();
    }

    private boolean hasRoutingEnd() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.FINISH).count() != 0;
    }

    private boolean hasRoutingEndResolved() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.FINISH && routingNode.getEdgeId() != -1).count() != 0;
    }

    private RoutingNode getRoutingEnd() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.FINISH).findFirst().get();
    }

    private boolean isCalcRoutePossible() {
        return hasRoutingStartResolved() && hasRoutingEndResolved();
    }

    private void startProgress(boolean withCancel) {
        mProgress.setVisible(true);
        if (withCancel) {
            mProgressStop.setVisible(true);
        }
    }

    private void stopProgress() {
        mProgress.setVisible(false);
        mProgressStop.setVisible(false);
    }

    private void updateListContent() {
        if (mQueryTask != null) {
            mQueryTask.cancel(true);
            mQueryTask = null;
        }
        mQueryItems.clear();
        if (mQueryText != null && mQueryText.length() > 1) {
            mQueryTask = new QueryTaskPOI(mQueryText, mFilterType, mAdminIdList);
            mQueryTask.setOnRunning((runningEvent) -> {
                mQueryListProgress.setVisible(true);
            });
            mQueryTask.setOnSucceeded((succeededEvent) -> {
                LogUtils.log("QueryTaskPOI::succeeded " + mQueryTask.getValue().size());
                mQueryListProgress.setVisible(false);
                mQueryItems.addAll(mQueryTask.getValue());
            });
            mQueryTask.setOnCancelled((cancelEvent) -> {
                LogUtils.log("QueryTaskPOI::cancel");
                mQueryListProgress.setVisible(false);
            });
            mExecutorService.submit(mQueryTask);
        }
    }

    private void addToLocationHistory(JsonArray coords) {
        if (mLocationHistory.size() > 0) {
            if (mLocationHistory.get(mLocationHistory.size() - 1).equals(coords)) {
                return;
            }
        }
        if (mLocationHistory.size() == 10) {
            mLocationHistory.remove(0);
        }
        mLocationHistory.add(coords);
        mLocationHistoryIndex = mLocationHistory.size() - 1;
        //LogUtils.log("locationHistory = " + mLocationHistory);
        Config.getInstance().put("locationHistory", mLocationHistory);
        Config.getInstance().save();
    }

    private void goBackInLocationHistory() {
        if (mLocationHistoryIndex != 0) {
            mLocationHistoryIndex--;
            JsonArray coords = (JsonArray) mLocationHistory.get(mLocationHistoryIndex);
            centerMapOnCoordinates(GISUtils.getDoubleValue(coords.get(0)), GISUtils.getDoubleValue(coords.get(1)));
        }
    }

    private void goForwardInLocationHistory() {
        if (mLocationHistoryIndex < mLocationHistory.size() - 1) {
            mLocationHistoryIndex++;
            JsonArray coords = (JsonArray) mLocationHistory.get(mLocationHistoryIndex);
            centerMapOnCoordinates(GISUtils.getDoubleValue(coords.get(0)), GISUtils.getDoubleValue(coords.get(1)));
        }
    }

    private void showShapeAtCenter() {
        Point2D mapCenterPos = mCenterPos;
        OSMShape s = findShapeAtPoint(mapCenterPos, OSMUtils.SELECT_AREA_TYPE);
        if (s != null) {
            doSelectShape(s);
        }
    }

    private void doSelectShape(OSMShape shape) {
        mSelectdOSMId = shape.getOSMId();
        JsonObject osmObject = mOSMObjects.get(mSelectdOSMId);
        if (osmObject != null) {
            JsonObject tags = (JsonObject) osmObject.get("tags");
            LogUtils.log("type = " + osmObject.get("type"));
            if (shape instanceof OSMPolyline) {
                // only show ways as selected not areas
                if (osmObject.get("type").equals("way")) {
                    mSelectdShape = shape;
                    mSelectdShape.setSelected();
                }
            }
            if (shape instanceof OSMPolygon) {
                if (shape.getAreaType() == OSMUtils.AREA_TYPE_BUILDING) {
                    mSelectdShape = shape;
                    mSelectdShape.setSelected();
                }
            }

            final StringBuffer s = new StringBuffer();
            s.append(shape.getInfoLabel(tags));

            if (shape instanceof OSMPolyline) {
                LogUtils.log("Polyline: " + mSelectdOSMId + "  " + tags);
            } else if (shape instanceof OSMPolygon) {
                LogUtils.log("Polygon: " + mSelectdOSMId + "  " + tags);
            } else if (shape instanceof OSMImageView) {
                LogUtils.log("Node: " + mSelectdOSMId + " " + tags);
            }
            infoLabel.setText(s.toString().trim());
            drawShapes();
        }
    }

    private String getAdminAreaStringAtPos(Point2D coordPos) {
        JsonArray adminDataList = getAdminAreaListAtPos(coordPos);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < adminDataList.size(); i++) {
            JsonObject adminData = (JsonObject) adminDataList.get(i);
            String name = (String) adminData.get("name");
            s.append(name);
            if (i < adminDataList.size() - 1) {
                s.append("/");
            }
        }
        return s.toString();
    }

    private JsonArray getAdminAreaListAtPos(Point2D coordPos) {
        JsonArray adminDataList = QueryController.getInstance().getAdminAreasAtPointWithGeom(coordPos.getX(), coordPos.getY(),
                "(8)", this);
        if (adminDataList.size() == 0) {
            adminDataList = QueryController.getInstance().getAdminAreasAtPointWithGeom(coordPos.getX(), coordPos.getY(),
                    "(6)", this);
        }
        JsonArray adminAreaList = new JsonArray();
        if (adminDataList.size() != 0) {
            JsonObject adminData = (JsonObject) adminDataList.get(0);
            JsonObject tags = (JsonObject) adminData.get("tags");
            if (tags != null) {
                adminAreaList.add(adminData);
                JsonArray parents = (JsonArray) tags.get("parents");
                if (parents != null) {
                    for (int j = 0; j < parents.size(); j++) {
                        JsonObject parentAdminArea = QueryController.getInstance().getAdminAreasWithId(GISUtils.getLongValue(parents.get(j)));
                        if (parentAdminArea != null) {
                            int parentAdminLevel = GISUtils.getIntValue(parentAdminArea.get("adminLevel"));
                            if (OSMUtils.mAdminLevelSet.contains(parentAdminLevel)) {
                                adminAreaList.add(parentAdminArea);
                            }
                        }
                    }
                }
            }
        }
        return adminAreaList;
    }

    private long getAdminIdAtPos(Point2D coordPos) {
        JsonArray adminDataList = QueryController.getInstance().getAdminAreasAtPointWithGeom(coordPos.getX(), coordPos.getY(),
                "(8)", this);
        if (adminDataList.size() == 0) {
            adminDataList = QueryController.getInstance().getAdminAreasAtPointWithGeom(coordPos.getX(), coordPos.getY(),
                    "(6)", this);
        }
        if (adminDataList.size() != 0) {
            JsonObject adminData = (JsonObject) adminDataList.get(0);
            LogUtils.log("getAdminIdAtPos " + adminData.get("name"));
            return GISUtils.getLongValue(adminData.get("osmId"));
        }
        return 0;
    }


    private void saveCurrentFilter() {
        Config.getInstance().put("filter", mFilterConfig);
        Config.getInstance().save();
    }

    private void applyFilterConfig() {
        if (mFilterConfig != null) {
            mNodePane.setVisible((boolean) mFilterConfig.get("poi"));
            mTilePane.setVisible((boolean) mFilterConfig.get("tile"));
            mLabelPane.setVisible((boolean) mFilterConfig.get("label"));
            mCountryPane.setVisible((boolean) mFilterConfig.get("country"));
            mAreaPane.setVisible((boolean) mFilterConfig.get("area"));
            mTransparentWays = !(boolean) mFilterConfig.get("way");
        } else {
            mFilterConfig = new JsonObject();
            mFilterConfig.put("poi", true);
            mFilterConfig.put("tile", true);
            mFilterConfig.put("label", true);
            mFilterConfig.put("country", true);
            mFilterConfig.put("area", true);
            mFilterConfig.put("way", true);
        }
    }

    private void updateFakeShapes() {
        mOutlineRect.setLayoutX(mMapZero.getX() - mScene.getWidth() / 2);
        mOutlineRect.setLayoutY(mMapZero.getY() - mScene.getHeight());

        mOutlineRect.setWidth(mScene.getWidth() * 3);
        mOutlineRect.setHeight(mScene.getHeight() * 3);

        mOutlineRect.setTranslateX(-mMapZero.getX());
        mOutlineRect.setTranslateY(-mMapZero.getY());
    }

    private HttpsURLConnection setupHttpsRequest(URL url) {
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_READ_TIMEOUT);
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            urlConnection.connect();
            int code = urlConnection.getResponseCode();
            if (code != HttpsURLConnection.HTTP_OK) {
                LogUtils.log("response:" + code);
                return null;
            }
            return urlConnection;
        } catch (Exception e) {
            LogUtils.error("Failed to connect to server " + url, e);
            return null;
        }
    }

    private void showSidePane() {
        sidePane.setVisible(true);
        rightPane.getChildren().add(0, sidePane);
        // TODO move in func
        Timer animTimer = new Timer();
        animTimer.scheduleAtFixedRate(new TimerTask() {
            int i = 0;

            @Override
            public void run() {
                if (i < 10) {
                    sidePane.setPrefWidth(Math.min(sidePane.getPrefWidth() + 30, 300));
                } else {
                    this.cancel();
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            mSidePaneExpanded = true;
                        }
                    });
                }
                i++;
            }

        }, 0, 25);
    }

    private void hideSidePane() {
        Timer animTimer = new Timer();
        animTimer.scheduleAtFixedRate(new TimerTask() {
            int i = 0;

            @Override
            public void run() {
                if (i < 10) {
                    sidePane.setPrefWidth(Math.max(0, sidePane.getPrefWidth() - 30));
                } else {
                    this.cancel();
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            sidePane.setVisible(false);
                            rightPane.getChildren().remove(0);
                            mSidePaneExpanded = false;
                        }
                    });
                }
                i++;
            }

        }, 0, 25);
    }

    private JsonObject getClosestEdgeOnPos(Point2D coordsPos) {
        JsonArray edgeList = QueryController.getInstance().getEdgeOnPos(coordsPos.getX(), coordsPos.getY(), 0.0005, 30, 20);
        if (edgeList.size() != 0) {
            return (JsonObject) edgeList.get(0);
        }
        return null;
    }

    private void createSelectedEdgeShape() {
        if (mSelectedEdge != null) {
            mSelectedEdgeShape = displayCoordsPolyline((JsonArray) mSelectedEdge.get("coords"));
            mSelectedEdgeShape.setStrokeWidth(2);
            mSelectedEdgeShape.setStroke(Color.GREEN);
        }
    }

    private void updateSavedNodeListContent() {
        mSavedNodesListItems.clear();
        for (RoutingNode node : mSavedNodes) {
            mSavedNodesListItems.add(node);
        }
    }

    private void updateRouteNodeListContent() {
        mRouteNodesListItems.clear();
        for (RoutingNode node : mRoutingNodes) {
            mRouteNodesListItems.add(node);
        }
        // TODO
        mRouteNodesListView.setPrefHeight(mRoutingNodes.size() * 48.0);
    }

    private void loadTileStyleConfig() {
        InputStream configFile = getClass().getResourceAsStream("tilestyle.json");
        if (configFile != null) {
            try {
                InputStreamReader reader = new InputStreamReader(configFile);
                mTileStyle = (JsonObject) Jsoner.deserialize(reader);
            } catch (Exception e) {
                LogUtils.error("loadTileStyleConfig", e);
            }
        }
    }

    private void clearRoute() {
        mRoutePolylineList.clear();
        mRouteEdgeIdList.clear();
        QueryController.getInstance().clearAllRoutes();
        storeRoutingNodes();
        mRouteAListItems.clear();
        mRouteBListItems.clear();
        mRouteATime.setText("");
        mRouteALength.setText("");
        mRouteBTime.setText("");
        mRouteBLength.setText("");
        drawShapes();
    }

    private int getMapZoomToShowRoute() {
        int zoom = mMapZoom;
        while (zoom >= MIN_ZOOM) {
            Point2D startPos = coordinateToDisplay(getRoutingStart().getCoordsPos(), zoom);
            Point2D endPos = coordinateToDisplay(getRoutingEnd().getCoordsPos(), zoom);

            double distance = Math.abs(startPos.distance(endPos));
            if (distance < mScene.getWidth() && distance < (mScene.getHeight() - bottomPane.getHeight() - topPane.getHeight())) {
                return zoom;
            }
            zoom--;
        }
        return mMapZoom;
    }

    private BoundingBox getRouteBoundingBox(int zoom) {
        Point2D startPos = coordinateToDisplay(getRoutingStart().getCoordsPos(), zoom);
        Point2D endPos = coordinateToDisplay(getRoutingEnd().getCoordsPos(), zoom);
        double minX = Math.min(startPos.getX(), endPos.getX());
        double minY = Math.min(startPos.getY(), endPos.getY());
        double width = Math.abs(startPos.getX() - endPos.getX());
        double height = Math.abs(startPos.getY() - endPos.getY());
        return new BoundingBox(minX - width / 2, minY - height / 2, width + width / 2, height + height / 2);
    }

    private BoundingBox getRouteBoundingBoxCoords(int zoom, BoundingBox bbox) {
        Point2D minPos = displayToCoordinate(bbox.getMinX(), bbox.getMinY(), zoom);
        Point2D maxPos = displayToCoordinate(bbox.getMaxX(), bbox.getMaxX(), zoom);
        return new BoundingBox(minPos.getX(), minPos.getY(), maxPos.getX(), maxPos.getY());
    }

    private void calcRoute(RoutingNode startPoint, RoutingNode endPoint) {
        CalcRouteTaskExternal task = new CalcRouteTaskExternal(startPoint.getEdgeId(), endPoint.getEdgeId());
        task.setOnSucceeded((event) -> {
            LogUtils.log("CalcRouteTaskExternal succeededEvent");
            stopProgress();
            mRouteEdgeIdList.clear();
            mRouteEdgeIdList.addAll(task.mRouteList);
            drawShapes();

            // change mapzoom to show route
            int zoom = getMapZoomToShowRoute();
            BoundingBox bbox = getRouteBoundingBox(zoom);
            Point2D center = new Point2D(bbox.getCenterX(), bbox.getCenterY());
            centerMapOnDisplayWithZoom(center, zoom);

            mRouteList.clear();
            mRouteAListItems.clear();
            mRouteBListItems.clear();
            mRouteATime.setText("");
            mRouteALength.setText("");
            mRouteBTime.setText("");
            mRouteBLength.setText("");
            QueryController.getInstance().loadRoute(this);
        });
        task.setOnRunning((event) -> {
            clearRoute();
            startProgress(true);
            mProgressStop.setOnAction(event1 -> task.destroy());
        });
        task.setOnCancelled((event) -> {
            LogUtils.log("CalcRouteTaskExternal cancelEvent");
            stopProgress();
        });
        mExecutorService.submit(task);
    }

    public void restoreRouteData(RouteUtils.TYPE type, JsonArray edgeIdList, JsonArray wayIdList,
                                 JsonObject streetTypeMap, JsonArray coords) {
        LogUtils.log("restoreRouteData " + type.name());
        Route route = new Route(getRoutingStart(), getRoutingEnd(), type,
                edgeIdList, wayIdList, streetTypeMap, coords);
        mRouteList.add(route);

        double time = 0.0;
        double length = 0.0;
        for (int i = 0; i < streetTypeMap.size(); i++) {
            double streetTypeLength = GISUtils.getDoubleValue(streetTypeMap.get(String.valueOf(i)));
            int speed = OSMUtils.getStreetTypeSpeed(i);
            double km = Math.ceil(streetTypeLength / 1000);
            time += km / (double) speed;
            length += streetTypeLength;
        }
        int hours = (int) time;
        int minutes = (int) ((time - hours) * 0.6 * 100);

        if (type.ordinal() == 0) {
            mRouteALength.setText((int) (length / 1000) + " km");
            mRouteATime.setText(hours + "h " + minutes + "m");
        }
        if (type.ordinal() == 1) {
            mRouteBLength.setText((int) (length / 1000) + " km");
            mRouteBTime.setText(hours + "h " + minutes + "m");
        }

        for (int i = 0; i < wayIdList.size(); i++) {
            JsonObject step = (JsonObject) wayIdList.get(i);
            if (type.ordinal() == 0) {
                mRouteAListItems.add(new RouteStep(step));
            }
            if (type.ordinal() == 1) {
                mRouteBListItems.add(new RouteStep(step));
            }
        }
    }

    private void createRouteAListContent() {
        HBox routeAStats = new HBox();
        routeAStats.setPadding(new Insets(10, 10, 10, 10));

        mRouteALength = new Text();
        mRouteALength.setStyle("-fx-font-size: 16");
        routeAStats.getChildren().add(mRouteALength);

        mRouteATime = new Text();
        mRouteATime.setStyle("-fx-font-size: 16");
        routeAStats.getChildren().add(mRouteATime);
        HBox.setMargin(mRouteATime, new Insets(0, 10, 0, 10));

        mRouteAContent.getChildren().add(routeAStats);

        mRouteAListView = new ListView<>();
        mRouteAListView.setItems(mRouteAListItems);
        mRouteAListView.setCellFactory(queryListView -> new RouteStepListViewCell(this));
        mRouteAListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<RouteStep>() {
            @Override
            public void changed(ObservableValue<? extends RouteStep> observable, RouteStep oldValue, RouteStep newValue) {
                if (newValue != null) {
                    Point2D coords = newValue.getCoordsPos();
                    centerMapOnCoordinatesWithZoom(coords.getX(), coords.getY(), 17);
                }
            }
        });

        mRouteAContent.getChildren().add(mRouteAListView);
    }

    private void createRouteBListContent() {
        HBox routeBStats = new HBox();
        routeBStats.setPadding(new Insets(10, 10, 10, 10));

        mRouteBLength = new Text();
        mRouteBLength.setStyle("-fx-font-size: 16");
        routeBStats.getChildren().add(mRouteBLength);

        mRouteBTime = new Text();
        mRouteBTime.setStyle("-fx-font-size: 16");
        routeBStats.getChildren().add(mRouteBTime);
        HBox.setMargin(mRouteBTime, new Insets(0, 10, 0, 10));

        mRouteBContent.getChildren().add(routeBStats);

        mRouteBListView = new ListView<>();
        mRouteBListView.setItems(mRouteBListItems);
        mRouteBListView.setCellFactory(queryListView -> new RouteStepListViewCell(this));
        mRouteBListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<RouteStep>() {
            @Override
            public void changed(ObservableValue<? extends RouteStep> observable, RouteStep oldValue, RouteStep newValue) {
                if (newValue != null) {
                    Point2D coords = newValue.getCoordsPos();
                    centerMapOnCoordinatesWithZoom(coords.getX(), coords.getY(), 17);
                }
            }
        });

        mRouteBContent.getChildren().add(mRouteBListView);
    }
}

