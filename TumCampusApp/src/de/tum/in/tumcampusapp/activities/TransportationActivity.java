package de.tum.in.tumcampusapp.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.auxiliary.Const;
import de.tum.in.tumcampusapp.models.managers.TransportManager;

/**
 * Activity to show transport stations and departures
 */
public class TransportationActivity extends Activity implements OnItemClickListener, OnItemLongClickListener {

	private EditText searchTextField;
	private ListView listViewResults;
	private ListView listViewSuggestionsAndSaved = null;
	private RelativeLayout progressLayout;
	private TransportManager transportaionManager;

	/**
	 * Check if a network connection is available or can be available soon
	 * 
	 * @return true if available
	 */
	public boolean connected() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();

		if (netInfo != null && netInfo.isConnectedOrConnecting()) {
			return true;
		}
		return false;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_transportation);

		// get all stations from db
		transportaionManager = new TransportManager(this);
		Cursor stationCursor = transportaionManager.getAllFromDb();

		searchTextField = (EditText) findViewById(R.id.activity_transport_searchfield);
		listViewResults = (ListView) findViewById(R.id.activity_transport_listview_result);
		listViewSuggestionsAndSaved = (ListView) findViewById(R.id.activity_transport_listview_suggestionsandsaved);
		progressLayout = (RelativeLayout) findViewById(R.id.activity_transportation_progress_layout);

		@SuppressWarnings("deprecation")
		ListAdapter adapterSuggestionsAndSaved = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1, stationCursor, stationCursor.getColumnNames(),
				new int[] { android.R.id.text1 });

		listViewSuggestionsAndSaved.setAdapter(adapterSuggestionsAndSaved);
		listViewSuggestionsAndSaved.setOnItemClickListener(this);
		listViewSuggestionsAndSaved.setOnItemLongClickListener(this);

		// initialize empty departure list, disable on click in list
		MatrixCursor departureCursor = new MatrixCursor(new String[] { "name", "desc", "_id" });
		@SuppressWarnings("deprecation")
		SimpleCursorAdapter adapterResults = new SimpleCursorAdapter(this, android.R.layout.two_line_list_item, departureCursor, departureCursor.getColumnNames(), new int[] {
				android.R.id.text1, android.R.id.text2 }) {

			@Override
			public boolean isEnabled(int position) {
				return false;
			}
		};
		listViewResults.setAdapter(adapterResults);
	}

	public void onClick(View view) {
		int viewId = view.getId();
		switch (viewId) {
		case R.id.activity_transport_dosearch:
			((InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
					searchTextField.getWindowToken(), 0);
			searchForStations(searchTextField.getText().toString());
			break;
		case R.id.activity_transport_domore:
			Cursor stationCursor = transportaionManager.getAllFromDb();
			SimpleCursorAdapter adapter = (SimpleCursorAdapter) listViewSuggestionsAndSaved.getAdapter();
			adapter.changeCursor(stationCursor);			
			listViewResults.setVisibility(View.GONE);
			listViewSuggestionsAndSaved.setVisibility(View.VISIBLE);
			break;
		}
	}

	public boolean searchForStations(final String inputText) {
		final Activity activity = this;
		progressLayout.setVisibility(View.VISIBLE);

		new Thread(new Runnable() {
			@Override
			public void run() {
				// Searchs station on website
				String message = "";
				TransportManager tm = new TransportManager(activity);
				Cursor stationCursor = null;
				try {
					if (!connected()) {
						throw new Exception(getString(R.string.no_internet_connection));
					}
					stationCursor = tm.getStationsFromExternal(inputText);
				} catch (Exception e) {
					// show errors in departures list
					MatrixCursor stationMatrixCursor = new MatrixCursor(new String[] { "name", "_id" });
					stationMatrixCursor.addRow(new String[] { e.getMessage(), "0" });
					stationCursor = stationMatrixCursor;
				}

				final Cursor finalStationCursor = stationCursor;
				final String message2 = message;

				// show stations from search result in station list
				// show error message if necessary
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						progressLayout.setVisibility(View.GONE);
						if (finalStationCursor != null) {
							SimpleCursorAdapter adapter = (SimpleCursorAdapter) listViewSuggestionsAndSaved.getAdapter();
							adapter.changeCursor(finalStationCursor);
							listViewSuggestionsAndSaved.setVisibility(View.VISIBLE);
							listViewResults.setVisibility(View.GONE);
						}
					}
				});
			}
		}).start();
		return false;
	}

	@Override
	public void onItemClick(final AdapterView<?> av, View v, int position, long id) {
		// click on station in list
		Cursor departureCursor = (Cursor) av.getAdapter().getItem(position);
		final String location = departureCursor.getString(departureCursor.getColumnIndex(Const.NAME_COLUMN));

		// save clicked station into db and refresh station list
		// (could be clicked on search result list)
		SimpleCursorAdapter adapter = (SimpleCursorAdapter) av.getAdapter();
		TransportManager tm = new TransportManager(this);
		tm.replaceIntoDb(location);
		adapter.changeCursor(tm.getAllFromDb());

		progressLayout.setVisibility(View.VISIBLE);

		new Thread(new Runnable() {
			@Override
			public void run() {
				// get departures from website
				TransportManager tm = new TransportManager(av.getContext());
				Cursor departureCursor = null;
				try {
					if (!connected()) {
						throw new Exception(getString(R.string.no_internet_connection));
					}
					departureCursor = tm.getDeparturesFromExternal(location);

				} catch (Exception e) {
					// show errors in departures list
					MatrixCursor departureMatrixCursor = new MatrixCursor(new String[] { "name", "desc", "_id" });
					departureMatrixCursor.addRow(new String[] { e.getMessage(), "", "0" });
					departureCursor = departureMatrixCursor;
				}

				// show departures in list
				final Cursor finalDepartureCursor = departureCursor;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						progressLayout.setVisibility(View.GONE);
						SimpleCursorAdapter adapter = (SimpleCursorAdapter) listViewResults.getAdapter();
						adapter.changeCursor(finalDepartureCursor);
						listViewSuggestionsAndSaved.setVisibility(View.GONE);
						listViewResults.setVisibility(View.VISIBLE);
					}
				});
			}
		}).start();
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> av, View v, final int position, long id) {

		// confirm and delete station
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {

				// delete station from list, refresh station list
				Cursor c = (Cursor) av.getAdapter().getItem(position);
				String location = c.getString(c.getColumnIndex(Const.NAME_COLUMN));

				TransportManager tm = new TransportManager(av.getContext());
				tm.deleteFromDb(location);

				SimpleCursorAdapter adapter = (SimpleCursorAdapter) av.getAdapter();
				adapter.changeCursor(tm.getAllFromDb());
			}
		};
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getString(R.string.really_delete));
		builder.setPositiveButton(getString(R.string.yes), listener);
		builder.setNegativeButton(getString(R.string.no), null);
		builder.show();
		return false;
	}
}