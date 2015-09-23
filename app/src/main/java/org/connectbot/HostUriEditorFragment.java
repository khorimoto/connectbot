/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.connectbot.bean.HostBean;
import org.connectbot.transport.AbsTransport;
import org.connectbot.transport.Local;
import org.connectbot.transport.SSH;
import org.connectbot.transport.Telnet;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;

public class HostUriEditorFragment extends Fragment {

	private static final String TAG = "HostUriEditorFragment";

	// Listener for host URI changes.
	private Listener mListener;

	// The URI data currently present in the widget.
	private UriData mUriData;

	// Whether the URI parts subsection is expanded.
	private boolean mIsExpanded = false;

	// Whether a text edit is in progress. When the quick-connect field is being edited, changes
	// automatically propagate to the URI part fields; likewise, when the URI part fields are
	// edited, changes are propagated to the quick-connect field. This boolean safeguards against
	// infinite loops which can be caused by one field changing the other field, which changes the
	// first field, etc.
	private boolean mTextEditInProgress = false;

	private Spinner mTransportSpinner;
	private TextInputLayout mQuickConnectContainer;
	private EditText mQuickConnectField;
	private ImageButton mExpandCollapseButton;
	private View mUriPartsContainer;
	private View mUsernameContainer;
	private EditText mUsernameField;
	private View mHostnameContainer;
	private EditText mHostnameField;
	private View mPortContainer;
	private EditText mPortField;

	public HostUriEditorFragment() {}

	/**
	 * Creates a HostUriEditorFragment. This static function should be used instead of calling the
	 * constructor directly.
	 * @param existingHost The existing host if one exists, or null if a new host is being created.
	 */
	public static HostUriEditorFragment createInstance(HostBean existingHost) {
		HostUriEditorFragment fragment = new HostUriEditorFragment();

		Bundle args = new Bundle();
		if (existingHost != null) {
			args.putString(HostDatabase.FIELD_HOST_PROTOCOL, existingHost.getProtocol());
			args.putString(HostDatabase.FIELD_HOST_USERNAME, existingHost.getUsername());
			args.putString(HostDatabase.FIELD_HOST_HOSTNAME, existingHost.getHostname());
			args.putString(HostDatabase.FIELD_HOST_NICKNAME, existingHost.getNickname());
			args.putInt(HostDatabase.FIELD_HOST_PORT, existingHost.getPort());
		}
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mUriData = new UriData();
		mUriData.protocol = getArguments().getString(HostDatabase.FIELD_HOST_PROTOCOL);
		mUriData.username = getArguments().getString(HostDatabase.FIELD_HOST_USERNAME);
		mUriData.hostname = getArguments().getString(HostDatabase.FIELD_HOST_HOSTNAME);
		mUriData.nickname = getArguments().getString(HostDatabase.FIELD_HOST_NICKNAME);
		mUriData.port = getArguments().getInt(HostDatabase.FIELD_HOST_PORT);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_host_uri_editor, container, false);

		mTransportSpinner = (Spinner) view.findViewById(R.id.transport_selector);
		ArrayAdapter<String> transportSelection = new ArrayAdapter<>(
				getActivity(),
				android.R.layout.simple_spinner_item,
				TransportFactory.getTransportNames());
		transportSelection.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTransportSpinner.setAdapter(transportSelection);
		mTransportSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String protocol = (String) mTransportSpinner.getSelectedItem();
				if (protocol == null) {
					// During initialization, protocol can be null before the list
					// of dropdown items has been generated.
					return;
				}

				mUriData.protocol = protocol;
				mUriData.port = TransportFactory.getTransport(protocol).getDefaultPort();

				mQuickConnectContainer.setHint(TransportFactory.getFormatHint(protocol, getActivity()));
				mQuickConnectField.setText(null);

				if (SSH.getProtocolName().equals(protocol)) {
					mUsernameContainer.setVisibility(View.VISIBLE);
					mHostnameContainer.setVisibility(View.VISIBLE);
					mPortContainer.setVisibility(View.VISIBLE);
					mExpandCollapseButton.setVisibility(View.VISIBLE);
				} else if (Telnet.getProtocolName().equals(protocol)) {
					mUsernameContainer.setVisibility(View.GONE);
					mHostnameContainer.setVisibility(View.VISIBLE);
					mPortContainer.setVisibility(View.VISIBLE);
					mExpandCollapseButton.setVisibility(View.VISIBLE);
				} else {
					setUriPartsContainerExpanded(false);
					mExpandCollapseButton.setVisibility(View.INVISIBLE);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		mQuickConnectContainer = (TextInputLayout) view.findViewById(R.id.quickconnect_field_container);
		mQuickConnectField = (EditText) view.findViewById(R.id.uri_field);
		mQuickConnectField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				if (mTransportSpinner.getSelectedItem() == null) {
					// During initialization,
					return;
				}

				String oldUriString = mUriData.toString();
				mUriData.applyQuickConnectString(
						s.toString(), (String) mTransportSpinner.getSelectedItem());

				if (!mTextEditInProgress) {
					mTextEditInProgress = true;
					mUsernameField.setText(mUriData.username);
					mHostnameField.setText(mUriData.hostname);
					mPortField.setText(Integer.toString(mUriData.port));
					mTextEditInProgress = false;
				}
			}
		});
		mExpandCollapseButton = (ImageButton) view.findViewById(R.id.expand_collapse_button);
		mExpandCollapseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setUriPartsContainerExpanded(!mIsExpanded);
			}
		});
		mUriPartsContainer = view.findViewById(R.id.uri_parts_container);
		mUsernameContainer = view.findViewById(R.id.username_field_container);
		mHostnameContainer = view.findViewById(R.id.hostname_field_container);
		mPortContainer = view.findViewById(R.id.port_field_container);
		mUsernameField = (EditText) view.findViewById(R.id.username_edit_text);
		mUsernameField.addTextChangedListener(new UriDataUpdater(HostDatabase.FIELD_HOST_USERNAME));
		mHostnameField = (EditText) view.findViewById(R.id.hostname_edit_text);
		mHostnameField.addTextChangedListener(new UriDataUpdater(HostDatabase.FIELD_HOST_HOSTNAME));
		mPortField = (EditText) view.findViewById(R.id.port_edit_text);
		mPortField.addTextChangedListener(new UriDataUpdater(HostDatabase.FIELD_HOST_PORT));

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			mListener = (Listener) getParentFragment();
		} catch (ClassCastException e) {
			throw new ClassCastException(getParentFragment().toString() + " must implement Listener");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	private void setUriPartsContainerExpanded(boolean expanded) {
		if (expanded) {
			mIsExpanded = true;
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_less);
			mUriPartsContainer.setVisibility(View.VISIBLE);
		} else {
			mIsExpanded = false;
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_more);
			mUriPartsContainer.setVisibility(View.GONE);
		}
	}

	public interface Listener {
		public void onValidAddressEntered(UriData data);
	}

	public static class UriData {
		public String protocol = SSH.getProtocolName();
		public String username;
		public String hostname;
		public String nickname;
		public int port = TransportFactory.getTransport(SSH.getProtocolName()).getDefaultPort();

		public void applyQuickConnectString(String quickConnectString, String protocol) {
			Uri uri = TransportFactory.getUri(protocol, quickConnectString);
			if (uri == null) {
				username = null;
				hostname = null;
				nickname = null;
				port = TransportFactory.getTransport(protocol).getDefaultPort();
				return;
			}

			HostBean host = TransportFactory.getTransport(protocol).createHost(uri);
			this.protocol = protocol;
			username = host.getUsername();
			hostname = host.getHostname();
			nickname = host.getNickname();
			port = host.getPort();
		}

		@Override
		public String toString() {
			int defaultPort = TransportFactory.getTransport(protocol).getDefaultPort();
			String usernameText = username == null ? "" : username;
			String hostnameText = hostname == null ? "" : hostname;
			String nicknameText = nickname == null ? "" : nickname;
			if (SSH.getProtocolName().equals(protocol)) {
				if (port == defaultPort)
					return usernameText + "@" + hostnameText;
				else
					return usernameText + "@" + hostnameText + ":" + port;
			} else if (Telnet.getProtocolName().equals(protocol)) {
				if (port == defaultPort)
					return hostnameText;
				else
					return hostnameText + ":" + port;
			} else if (Local.getProtocolName().equals(protocol)) {
				return nicknameText;
			} else {
				throw new RuntimeException("Invalid protocol");
			}
		}
	}

	private class UriDataUpdater implements TextWatcher {

		private final String mFieldType;

		public UriDataUpdater(String fieldType) {
			mFieldType = fieldType;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}

		@Override
		public void afterTextChanged(Editable s) {
			String text = s.toString();

			if (HostDatabase.FIELD_HOST_USERNAME.equals(mFieldType)) {
				mUriData.username = text;
			} else if (HostDatabase.FIELD_HOST_HOSTNAME.equals(mFieldType)) {
				mUriData.hostname = text;
			} else if (HostDatabase.FIELD_HOST_PORT.equals(mFieldType)) {
				try {
					mUriData.port = Integer.parseInt(text);
				} catch (NumberFormatException e) {}
			} else {
				throw new RuntimeException("Invalid field type.");
			}

			if (!mTextEditInProgress) {
				mTextEditInProgress = true;
				mQuickConnectField.setText(mUriData.toString());
				mTextEditInProgress = false;
			}
		}
	}

}
