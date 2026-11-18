package com.example.tugas9;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends AppCompatActivity implements MapEventsReceiver {

    private MapView mapView;
    private EditText etSearch;
    private ImageView btnClear;
    private MaterialCardView cardInfo;
    private TextView tvPlaceName, tvPlaceAddress, tvDistance, tvRouteInfo;
    private MaterialButton btnRoute, btnClose;

    private Marker destinationMarker;
    private Marker originMarker;
    private Polyline routeLine;
    private MyLocationNewOverlay locationOverlay;
    private FloatingActionButton fabMyLocation;
    private GeoPoint myLocation;
    private GeoPoint currentDestination;
    private int baseBottomMargin = 24;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) initLocation();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setupOsmdroid();
        setContentView(R.layout.activity_maps);

        etSearch = findViewById(R.id.etSearch);
        btnClear = findViewById(R.id.btnClear);
        cardInfo = findViewById(R.id.cardInfo);
        tvPlaceName = findViewById(R.id.tvPlaceName);
        tvPlaceAddress = findViewById(R.id.tvPlaceAddress);
        tvDistance = findViewById(R.id.tvDistance);
        tvRouteInfo = findViewById(R.id.tvRouteInfo);
        btnRoute = findViewById(R.id.btnRoute);
        btnClose = findViewById(R.id.btnClose);
        fabMyLocation = findViewById(R.id.fabMyLocation);
        MaterialCardView cardSearch = findViewById(R.id.cardSearch);

        applyWindowInsets(cardSearch, fabMyLocation, cardInfo);
        setupMap();
        setupSearch();

        fabMyLocation.setOnClickListener(v -> goToMyLocation());

        btnRoute.setOnClickListener(v -> {
            if (currentDestination != null && myLocation != null) {
                fetchRoute(myLocation, currentDestination);
            } else if (myLocation == null) {
                Toast.makeText(this, "Lokasi Anda belum ditemukan", Toast.LENGTH_SHORT).show();
            }
        });

        btnClose.setOnClickListener(v -> clearAll());

        requestLocationPermission();
    }

    private void applyWindowInsets(View searchCard, View fab, View bottomCard) {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            FrameLayout.LayoutParams searchLp =
                    (FrameLayout.LayoutParams) searchCard.getLayoutParams();
            searchLp.topMargin = sys.top + 12;
            searchCard.setLayoutParams(searchLp);

            baseBottomMargin = sys.bottom + 24;
            FrameLayout.LayoutParams fabLp =
                    (FrameLayout.LayoutParams) fab.getLayoutParams();
            fabLp.bottomMargin = baseBottomMargin;
            fab.setLayoutParams(fabLp);

            bottomCard.setPadding(0, 0, 0, sys.bottom);

            return insets;
        });
    }

    private void setupOsmdroid() {
        IConfigurationProvider config = Configuration.getInstance();
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        config.setUserAgentValue("Tugas9MapsApp/1.0 (Android; university project)");
        File base = new File(getCacheDir(), "osmdroid");
        base.mkdirs();
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(new File(base, "tiles"));
    }

    private void setupMap() {
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.OpenTopo);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(5.0);
        mapView.getController().setCenter(new GeoPoint(-2.5, 118.0));
        mapView.getOverlays().add(new MapEventsOverlay(this));

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);
    }

    private void setupSearch() {
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v);
                doSearch(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            clearAll();
        });
    }

    // --- Search ---

    private void doSearch(String query) {
        if (query.isEmpty()) return;

        clearRoute();

        GeoPoint parsed = parseCoordinates(query);
        if (parsed != null) {
            showPlace(parsed.getLatitude(), parsed.getLongitude(), "Lokasi", query);
            return;
        }

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            @SuppressWarnings("deprecation")
            List<Address> results = geocoder.getFromLocationName(query, 1);
            if (results != null && !results.isEmpty()) {
                Address addr = results.get(0);
                String name = addr.getFeatureName() != null ? addr.getFeatureName() : query;
                String address = addr.getAddressLine(0) != null ? addr.getAddressLine(0) : "";
                showPlace(addr.getLatitude(), addr.getLongitude(), name, address);
            } else {
                Toast.makeText(this, "Lokasi tidak ditemukan", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Gagal mencari. Periksa koneksi internet.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private GeoPoint parseCoordinates(String input) {
        try {
            String[] parts = input.split("[,\\s]+");
            if (parts.length >= 2) {
                double lat = Double.parseDouble(parts[0].trim());
                double lng = Double.parseDouble(parts[1].trim());
                if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                    return new GeoPoint(lat, lng);
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    // --- Show place ---

    private void showPlace(double lat, double lng, String name, String address) {
        currentDestination = new GeoPoint(lat, lng);

        if (destinationMarker != null) mapView.getOverlays().remove(destinationMarker);
        destinationMarker = new Marker(mapView);
        destinationMarker.setPosition(currentDestination);
        destinationMarker.setTitle(name);
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(destinationMarker);

        mapView.getController().setZoom(Math.max(mapView.getZoomLevelDouble(), 15.0));
        mapView.getController().animateTo(currentDestination);
        mapView.invalidate();

        tvPlaceName.setText(name);
        tvPlaceAddress.setText(address);
        tvRouteInfo.setVisibility(View.GONE);
        btnRoute.setVisibility(View.VISIBLE);

        if (myLocation != null) {
            float meters = distanceBetween(
                    myLocation.getLatitude(), myLocation.getLongitude(), lat, lng);
            tvDistance.setText(formatDistance(meters));
            tvDistance.setVisibility(View.VISIBLE);
        } else {
            tvDistance.setVisibility(View.GONE);
        }

        cardInfo.setVisibility(View.VISIBLE);

        cardInfo.post(() -> {
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) fabMyLocation.getLayoutParams();
            lp.bottomMargin = cardInfo.getHeight() + baseBottomMargin;
            fabMyLocation.setLayoutParams(lp);
        });
    }

    // --- Routing (OSRM) ---

    private void fetchRoute(GeoPoint from, GeoPoint to) {
        btnRoute.setText("Memuat rute...");
        btnRoute.setEnabled(false);

        new Thread(() -> {
            try {
                String urlStr = String.format(Locale.US,
                        "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                        from.getLongitude(), from.getLatitude(),
                        to.getLongitude(), to.getLatitude());

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "Tugas9MapsApp/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray routes = json.getJSONArray("routes");

                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    double distMeters = route.getDouble("distance");
                    double durSeconds = route.getDouble("duration");

                    JSONArray coords = route.getJSONObject("geometry")
                            .getJSONArray("coordinates");

                    ArrayList<GeoPoint> points = new ArrayList<>();
                    for (int i = 0; i < coords.length(); i++) {
                        JSONArray c = coords.getJSONArray(i);
                        points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                    }

                    runOnUiThread(() -> drawRoute(points, distMeters, durSeconds));
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Rute tidak ditemukan", Toast.LENGTH_SHORT).show();
                        resetRouteButton();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Gagal memuat rute", Toast.LENGTH_SHORT).show();
                    resetRouteButton();
                });
            }
        }).start();
    }

    private void drawRoute(ArrayList<GeoPoint> points, double distMeters, double durSeconds) {
        clearRoute();

        routeLine = new Polyline();
        routeLine.setPoints(points);
        routeLine.getOutlinePaint().setColor(Color.parseColor("#4285F4"));
        routeLine.getOutlinePaint().setStrokeWidth(12f);
        routeLine.getOutlinePaint().setAntiAlias(true);
        mapView.getOverlays().add(0, routeLine);

        if (originMarker != null) mapView.getOverlays().remove(originMarker);
        originMarker = new Marker(mapView);
        originMarker.setPosition(myLocation);
        originMarker.setTitle("Lokasi Anda");
        originMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(originMarker);

        BoundingBox box = BoundingBox.fromGeoPoints(points);
        mapView.zoomToBoundingBox(box.increaseByScale(1.3f), true);
        mapView.invalidate();

        String distText = formatDistance((float) distMeters);
        String durText = formatDuration(durSeconds);
        tvRouteInfo.setText(durText + "  •  " + distText);
        tvRouteInfo.setVisibility(View.VISIBLE);
        tvDistance.setVisibility(View.GONE);
        btnRoute.setVisibility(View.GONE);
    }

    private void clearRoute() {
        if (routeLine != null) {
            mapView.getOverlays().remove(routeLine);
            routeLine = null;
        }
        if (originMarker != null) {
            mapView.getOverlays().remove(originMarker);
            originMarker = null;
        }
        resetRouteButton();
    }

    private void clearAll() {
        clearRoute();
        if (destinationMarker != null) {
            mapView.getOverlays().remove(destinationMarker);
            destinationMarker = null;
        }
        currentDestination = null;
        cardInfo.setVisibility(View.GONE);
        tvRouteInfo.setVisibility(View.GONE);
        mapView.invalidate();

        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) fabMyLocation.getLayoutParams();
        lp.bottomMargin = baseBottomMargin;
        fabMyLocation.setLayoutParams(lp);
    }

    private void resetRouteButton() {
        btnRoute.setText("Rute ke sini");
        btnRoute.setEnabled(true);
        btnRoute.setVisibility(View.VISIBLE);
    }

    // --- Map tap ---

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint p) {
        clearRoute();
        String name = "Lokasi dipilih";
        String address = String.format(Locale.US, "%.6f, %.6f", p.getLatitude(), p.getLongitude());

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            @SuppressWarnings("deprecation")
            List<Address> results = geocoder.getFromLocation(p.getLatitude(), p.getLongitude(), 1);
            if (results != null && !results.isEmpty()) {
                Address addr = results.get(0);
                if (addr.getFeatureName() != null) name = addr.getFeatureName();
                if (addr.getAddressLine(0) != null) address = addr.getAddressLine(0);
            }
        } catch (IOException ignored) {
        }

        showPlace(p.getLatitude(), p.getLongitude(), name, address);
        return true;
    }

    @Override
    public boolean longPressHelper(GeoPoint p) {
        return false;
    }

    // --- Location ---

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            initLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void initLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        @SuppressWarnings("deprecation")
        Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last == null) {
            @SuppressWarnings("deprecation")
            Location network = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            last = network;
        }
        if (last != null) {
            myLocation = new GeoPoint(last.getLatitude(), last.getLongitude());
            mapView.getController().setZoom(15.0);
            mapView.getController().animateTo(myLocation);
        }

        LocationListener listener = location -> {
            myLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
            if (mapView.getZoomLevelDouble() < 10) {
                mapView.getController().setZoom(15.0);
                mapView.getController().animateTo(myLocation);
            }
        };

        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, listener);
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, listener);
        }
    }

    private void goToMyLocation() {
        if (myLocation != null) {
            mapView.getController().setZoom(17.0);
            mapView.getController().animateTo(myLocation);
        } else {
            Toast.makeText(this, "Mencari lokasi...", Toast.LENGTH_SHORT).show();
            requestLocationPermission();
        }
    }

    // --- Util ---

    private float distanceBetween(double lat1, double lng1, double lat2, double lng2) {
        Location a = new Location("a");
        a.setLatitude(lat1);
        a.setLongitude(lng1);
        Location b = new Location("b");
        b.setLatitude(lat2);
        b.setLongitude(lng2);
        return a.distanceTo(b);
    }

    private String formatDistance(float meters) {
        if (meters >= 1000) {
            return String.format(Locale.getDefault(), "%.1f km", meters / 1000);
        }
        return String.format(Locale.getDefault(), "%.0f m", meters);
    }

    private String formatDuration(double seconds) {
        int total = (int) seconds;
        if (total < 60) return total + " dtk";
        int min = total / 60;
        if (min < 60) return min + " mnt";
        int hr = min / 60;
        min = min % 60;
        return hr + " jam " + min + " mnt";
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (locationOverlay != null) locationOverlay.enableMyLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (locationOverlay != null) locationOverlay.disableMyLocation();
    }
}
