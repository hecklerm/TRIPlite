/*
 * The MIT License
 *
 * Copyright 2016 Mark A. Heckler
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.thehecklers.triplite.ws;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.thehecklers.triplite.Triplite;
import org.thehecklers.triplite.model.Reading;

import static org.thehecklers.triplite.Triplite.logIt;

/**
 * WsControlClient connects with Cloud application service via WebSocket. It
 * provides the means by which to send readings from Triplite to the Cloud and
 * receive commands from the Cloud interface(s).
 *
 * @author Mark Heckler
 */
@ClientEndpoint
public class WsControlClient implements Observer {
    private Session session = null;
    private WebSocketContainer container;
    private boolean isConnected = false;
    static private String uriWeb;
    static private String nodeId;
    static private Triplite remote;

    public WsControlClient() {
        // No-parameter constructor for testing
        //logIt("In no parameter constructor of Control WS client.");
    }

    public WsControlClient(String uriWeb, String nodeId, Triplite remote) {
        WsControlClient.uriWeb = uriWeb.endsWith("/") ? uriWeb + "control" : uriWeb + "/" + "control";
        WsControlClient.nodeId = nodeId;
        WsControlClient.remote = remote;
        try {
            connectToWebSocketServer();
        } catch (Exception e) {
            logIt("Error connecting to Control WebSocket server: " + e.getLocalizedMessage());
        }
    }

    private void connectToWebSocketServer() throws Exception {
        try {
            container = ContainerProvider.getWebSocketContainer();

            logIt("Connecting to " + uriWeb);
            session = container.connectToServer(WsControlClient.class, URI.create(uriWeb));
        } catch (Exception e) {
            //(IOException | DeploymentException | IllegalStateException e) {
            logIt("Error connecting, " + uriWeb + ": " + e.getLocalizedMessage());
            isConnected = false;
            return;
        }

        isConnected = true;
        logIt("Control WebSocket connected: " + uriWeb);
    }

    public void disconnect() {
        if (session != null) {
            if (session.isOpen()) {
                try {
                    session.close();
                } catch (IOException e) {
                    Logger.getLogger(WsControlClient.class.getName()).log(Level.SEVERE, null, e);
                }
                logIt("Disconnecting: Control WebSocket session now closed");
            } else {
                logIt("Disconnecting: Control WebSocket session was already closed");
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        logIt("Control WebSocket connected to endpoint: " + session.getBasicRemote());
    }

    /*
        Messages contains a specific order for TRIP:
            F: Forward
            B: Backward
            L: turn Left
            R: turn Right
            f: ping forward (range finder)
            l: ping left    (range finder)
            r: ping right   (range finder)
            S: Stop         (excepting a few other test chars, any character will stop the robot)
    */
    @OnMessage
    public void onMessage(String message) {
        logIt("Control message received: '" + message + "'");

        if (message == null || message.length() != 1) {
            logIt("CONTROL FAIL: Message length ==" + message.length());
            return;
        }
        logIt("Writing to serial: " + message);
        try {
            remote.addToCommand(message);
        } catch (Exception ex) {
            Logger.getLogger(WsControlClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @OnError
    public void onError(Throwable t) {
        logIt("Error in Control WebSocket: " + t.getLocalizedMessage());
    }

    @Override
    public void update(Observable o, Object arg) {
        // Periodic check to verify the Control WebSocket is still connected; if not, reconnect

        if (isConnected) {
            try {
                if (session != null
                        && session.isOpen()
                        && session.getBasicRemote() != null) {
                    isConnected = true;
                } else {
                    if (session == null) {
                        logIt("Control WebSocket has session NULL");
                    } else {
                        if (!session.isOpen()) {
                            logIt("Control WebSocket has session CLOSED");
                        } else {
                            logIt("Control WebSocket has BasicRemote NULL");
                        }
                    }
                    // These cases all denote lack of/failed connection
                    isConnected = false;
                }
            } catch (Exception e) {
                logIt("Exception in Control WebSocket: " + e.getLocalizedMessage());
                isConnected = false;
            }
        } else {
            logIt("Trying to reconnect to Control WebSocket...");
            try {
                connectToWebSocketServer();
            } catch (Exception e) {
                logIt("Error connecting to Control WebSocket: " + e.getLocalizedMessage());
            }
        }
    }

//    @Override
//    public void onOpen(Session sn, EndpointConfig ec) {
//        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//        logIt("Opening /control endpoint...");
//    }
}
