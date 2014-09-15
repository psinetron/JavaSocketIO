JavaSocketIO
============

Websocket client for Android


#How to use:#


        SocketIO socketio = new SocketIO() {
        @Override
        public void onConnect() {

        }

        @Override
        public void onDisconnect() {

        }

        @Override
        public void onMessage(String message) {
             Log.d("===Server Answer====",message);
        }
    };
    
    socketio.Connect("192.168.0.1", 9000);

