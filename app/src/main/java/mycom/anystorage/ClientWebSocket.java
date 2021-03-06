package mycom.anystorage;


import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by KTS on 2016-09-26.
 */
public class ClientWebSocket {
    private static ClientWebSocket ourInstance = new ClientWebSocket();
    private Socket device;
    public static ClientWebSocket getInstance() {
        return ourInstance;
    }

    private String userId;
    private String userPwd;
    private Activity activity;
    private ClientWebSocket() {}
    private boolean isSuccess;
    private boolean flag = true;
    private boolean device_ack = true;
    private long cnt;
    public boolean isConnect(){ return device.connected(); }
    public boolean connect(Activity activity, String url, String userId, String userPwd){
        this.activity = activity;
        this.userId = userId;
        this.userPwd = userPwd;

        try{
            device = IO.socket(url);
            device.connect();
            init();
            new AutoLogin().start();
            return true;
        } catch (URISyntaxException e) {
            Log.e("Web Socket Error : ", e.toString());
            return false;
        }
    }
    public boolean login(String userId, String userPwd){
        this.userId = userId;
        this.userPwd = userPwd;
        return this.login();
    }
    public boolean login(){
        JSONObject loginData = new JSONObject();

        isSuccess = false;
        try {

            loginData.put("userId", this.userId);
            loginData.put("userPwd", this.userPwd);

            device.emit("device_login", loginData);
            flag = true;

            // Receive Ready
            while(flag) {
                try{
                    Thread.sleep(500);
                }catch(Exception e){}
            }

            if(isSuccess){
                AccountManager manager = AccountManager.getInstance();
                Log.e("userID : ", userId);
                Log.e("userPASS : ", userPwd);
                if(manager.setString("userId", this.userId))    Log.e("Save ID : ", "true");
                else                                            Log.e("Save ID : ", "false");

                if(manager.setString("userPwd", this.userPwd))  Log.e("Save Pwd : ", "true");
                else                                            Log.e("Save Pwd : ", "false");

                try {
                    String device_serial = (String) Build.class.getField("SERIAL").get(null);
                    device.on(userId + device_serial, new Emitter.Listener() {
                        @Override
                        public void call(Object... args) {
                            Log.e("device connect request!!!", "122-+");
                            device.emit("ack_connect_device", args[0]);
                        }
                    });
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                // Send Power On Message to Web Browser
                sendPowerOnMsg();
                return true;
            }else return false;
        } catch (JSONException e) {
            Log.e("Login Error : ", e.toString());
            return false;
        }
    }

    public void logout(){
        device.emit("logout_FileServer", new JSONObject());
    }
    public void powerOff(){
        logout();
        device.disconnect();
    }
    // Send Power On Message to Web Browser Method
    private void sendPowerOnMsg(){
        JSONObject res = new JSONObject();
        try {
            res.put("device_name", Build.DEVICE);
            res.put("device_model", Build.MODEL);
            res.put("device_serial", (String)Build.class.getField("SERIAL").get(null));
            device.emit("res_on_device", res);
        } catch (JSONException e) {
            Log.e("req_on_device Error : ", e.toString());
        }
        catch (NoSuchFieldException e) {
            Log.e("req_on_device Error : ", e.toString());
//            e.printStackTrace();
        } catch (IllegalAccessException e) {
            Log.e("req_on_device Error : ", e.toString());
//            e.printStackTrace();
        }
    }

    // Send Power Off Message to Web Browser Method
    private void sendPowerOffMsg(){
        JSONObject res = new JSONObject();
        try {
            res.put("device_name", Build.DEVICE);
            res.put("device_model", Build.MODEL);
            device.emit("res_off_device", res);
        } catch (JSONException e) {
            Log.e("req_on_device Error : ", e.toString());
        }
    }

    private void init(){
        // Initialize Socket
        device.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                // call register event
                device.on("type", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        JSONObject obj = new JSONObject();
                        device.emit("device", obj.toString());
                    }
                });
                device.on("req_on_device", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        // Send Power On Message to Web Browser
                        sendPowerOnMsg();
                    }
                });
                device.on("login_response", new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        try {
//                    JSONObject obj = new JSONObject(args[0].toString());
                            JSONObject obj = (JSONObject) args[0];
                            Log.e("Data ::::", obj.toString());
                            isSuccess = obj.getBoolean("isSuccess");

                        } catch (JSONException e) {
                            Log.e("Login Error : ", e.toString());
                            isSuccess = false;
                        }
                        flag = false;
                    }
                });

            }
        });
        device.on("req_file_tree", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.e("request_file tree", " ");
                File rootFile = Environment.getExternalStorageDirectory();
//                Log.e("rootFile", rootFile.l)

                JSONArray tree = getFileTree(rootFile);
                Log.e("create file tree", "   ");
                device.emit("res_file_tree", tree.toString());
                Log.e("send file tree", "   ");
            }
        });
        device.on("mkdir", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject resObj = new JSONObject();
                boolean isComplte = false;
                SimpleDateFormat format = new SimpleDateFormat("kkmmss");
                try {
                    JSONObject obj = (JSONObject) args[0];
                    String path = obj.getString("createPath");
                    String abPath = Environment.getExternalStorageDirectory().getCanonicalPath();
                    abPath = abPath.concat(path).concat("newForder").concat(format.format(new Date()));
                    File file = new File(abPath);

                    if(file.mkdir()){
                        isComplte = true;
                        resObj.put("tree", getFileTree(Environment.getExternalStorageDirectory()));
                    }
                    resObj.put("isComplete", isComplte);
                } catch (Exception e) {
                    Log.e("mkDir Error : ", e.toString());
                }
                device.emit("res_mkdir", resObj);
            }
        });
        device.on("rename", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject res = new JSONObject();
                boolean isComplete = false;
                try {
                    Log.e("Rename", "request");
                    JSONObject obj = (JSONObject) args[0];
                    String rootPath = Environment.getExternalStorageDirectory().getCanonicalPath();
                    String newPath = obj.getString("newName");
                    String oldPath = obj.getString("oldName");
                    newPath = rootPath.concat(newPath);
                    oldPath = rootPath.concat(oldPath);

                    File oldFile = new File(oldPath);
                    File newFile = new File(newPath);
                    Log.e("oldName : ", oldPath);
                    Log.e("newName : ", newPath);
                    if (oldFile.renameTo(newFile)) {
                        isComplete = true;
                        res.put("tree", getFileTree(Environment.getExternalStorageDirectory()));
                    } else {
                        isComplete = false;
                    }
                    res.put("isComplete", isComplete);
                }catch(Exception e){
                    Log.e("rename Error : ", e.toString());
                }
                device.emit("res_rename", res);
            }
        });
        device.on("cut_paste", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject resObj = new JSONObject();
                boolean isComplete = false;
                Log.e("cut paste", "request");
                int cnt = 0;
                try {
                    JSONObject obj = (JSONObject) args[0];
                    JSONArray oldArr = obj.getJSONArray("oldPaths");
                    JSONArray newArr = obj.getJSONArray("pastePaths");
                    Log.e("kts1 : ", oldArr.toString());
                    Log.e("kts2 : ", newArr.toString());
                    String oldPaths[] = new String[oldArr.length()] ;
                    String pastePaths[] = new String[newArr.length()];
                    String rootPath = Environment.getExternalStorageDirectory().getCanonicalPath();
                    for(int i = 0; i<oldPaths.length; i++) {
                        oldPaths[i] = oldArr.getString(i);
                        oldPaths[i] = rootPath.concat(oldPaths[i]);
                        pastePaths[i] = newArr.getString(i);
                        pastePaths[i] = rootPath.concat(pastePaths[i]);
                        File originFile = new File(oldPaths[i]);
                        File newFile = new File(pastePaths[i]);
                        if (originFile.renameTo(newFile)) {
                            cnt++;

                        }
                    }
                    resObj.put("total", oldPaths.length);
                    resObj.put("cnt", cnt);
                    if(cnt == oldPaths.length){
                        isComplete = true;
                        resObj.put("tree", getFileTree(Environment.getExternalStorageDirectory()));
                    }
                    resObj.put("isComplete", isComplete);
                }catch(Exception e){
                    Log.e("cut paste Error", e.toString());
                }
                device.emit("response_paste", resObj);
            }
        });
        device.on("copy_paste", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject res = new JSONObject();
                try {

                    JSONObject reqData = (JSONObject)args[0];
                    JSONArray cpArr = reqData.getJSONArray("oldPaths");
                    JSONArray paArr = reqData.getJSONArray("pastePaths");
                    String basePath = Environment.getExternalStorageDirectory().getCanonicalPath();
                    String []copyPaths = new String[cpArr.length()];
                    String []pastePaths = new String[paArr.length()];
                    File sourceFile, newFile;
                    boolean flag = true;
                    for(int idx = 0; idx < copyPaths.length; idx++) {
                        copyPaths[idx] = basePath.concat(cpArr.getString(idx));
                        pastePaths[idx] = basePath.concat(paArr.getString(idx));
                    }

//                    for(int idx = 0; idx < pastePaths.length; idx++)
                    for(int idx = 0; idx< copyPaths.length; idx++){
                        sourceFile = new File(copyPaths[idx]);
                        newFile = new File(pastePaths[idx]);
                        long i = 1;
                        // File Name Check
                        while(newFile.exists()){
                            // is Directory
                            if(newFile.isDirectory()){
                                newFile = new File(pastePaths[idx]+"("+i+")");
                                Log.e("path : ", newFile.getCanonicalPath());
                            }else if(newFile.isFile()){
                                String extType, name, base;

                                int lastDirPoint = pastePaths[idx].lastIndexOf("/");
//                                base = pastePaths[idx].substring(0,lastDirPoint);
                                base = newFile.getCanonicalPath();
                                base = base.substring(0, base.lastIndexOf("/"));
                                name = newFile.getName();
//                                name = pastePaths[idx].substring(lastDirPoint+1, pastePaths[idx].length());
                                int point = name.lastIndexOf(".");
                                //
                                if(point > 0){
                                    extType = name.substring(point, name.length());
                                    name = name.substring(0, point);
                                    newFile = new File(base+"/"+name+"("+i+")"+extType);
                                }else{
                                    newFile = new File(base+"/"+name+"("+i+")");
                                }

                            }
                            i++;
                        }
                       flag =  copyFile(sourceFile, newFile);
                    }

                    res.put("isComplete", flag);
                    res.put("tree", getFileTree(Environment.getExternalStorageDirectory()));
                }catch(Exception e){
                    Log.e("copy error : ", e.toString());
                }
                device.emit("res_copy", res);
            }
        });
        device.on("update_tree", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.e("update_file tree", " ");
                File rootFile = Environment.getExternalStorageDirectory();
                JSONArray tree = getFileTree(rootFile);
                Log.e("update file tree", "   ");
                device.emit("res_file_tree", tree.toString());
                Log.e("send file tree", "   ");
            }
        });

        device.on("req_file", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject res = new JSONObject();
                try {
                    int chunkSize = 1024 * 96;
                    JSONObject reqData = (JSONObject) args[0];
                    String rootPath = Environment.getExternalStorageDirectory().getCanonicalPath();
                    String filePath = reqData.getString("filePath");
                    String fileName = reqData.getString("fileName");
                    String client_id = reqData.getString("client_id");

                    String path = new String();
                    path = path.concat(rootPath).concat(filePath).concat(fileName);
                    File file = new File(path);

                    FileInputStream out = new FileInputStream(file);
                    ByteArrayOutputStream write = new ByteArrayOutputStream();
                    int totalChunk;
                    long totalSize = file.length();
                    int idx = 1;
                    byte []fileBin = new byte[chunkSize];
                    byte []total;
                    int len;
//                    String base64;
                    String key = Base64.encodeToString(filePath.concat(fileName).getBytes(),Base64.DEFAULT);


                    totalChunk = (int)(totalSize / chunkSize);
                    if(totalSize % chunkSize != 0) totalChunk++;

                    res.put("fileName", fileName);
                    res.put("totalChunk", totalChunk);
                    res.put("key", client_id);
                    device.emit("res_file_info", res);
//                    while((len = out.read(fileBin)) != -1) {
//                        write.write(fileBin, 0, len);
//                    }
//                    total = write.toByteArray();
//                    String base64 = Base64.encodeToString(total, Base64.DEFAULT);
//                    JSONObject streamObj = new JSONObject();
//                    streamObj.put("client_id", client_id);
//                    streamObj.put("fileName", fileName);
//                    streamObj.put("buffered", total);
//                    device.emit("res_file_stream", streamObj);

                    while((len = out.read(fileBin)) != -1) {
//                        write.write(fileBin, 0, len);
                        total = new byte[len];
                        for(int i = 0; i<len; i++)
                            total[i] = fileBin[i];
                        JSONObject chunk;

//                        total = write.toByteArray();
//                        write.flush();
                        chunk = new JSONObject();
                        chunk.put("client_id", client_id);
                        chunk.put("key", client_id);
                        chunk.put("idx", idx);
                        chunk.put("chunk", total);
                        idx++;
                        device.emit("res_file_chunk", chunk);

                    }


                }catch(Exception e){
                    Log.e("request file error : ", e.toString());
                }
//                device.emit("res_file", res);
            }
        });
        device.on("req_rm", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject resObj = new JSONObject();
                try {
                    JSONObject reqData = (JSONObject) args[0];
                    JSONArray rmArr = reqData.getJSONArray("rmPaths");
                    String []rmPath = new String[rmArr.length()];
                    boolean flag = true;
                    for(int i = 0; i<rmPath.length; i++) {
                        rmPath[i] = rmArr.getString(i);
                        rmPath[i] = Environment.getExternalStorageDirectory().getCanonicalPath().concat(rmPath[i]);
                        File root = new File(rmPath[i]);
                        flag = deleteFile(root);
                    }
                    resObj.put("isComplete", flag);
                    resObj.put("tree", getFileTree(Environment.getExternalStorageDirectory()));
                }catch(Exception e){
                    Log.e("rm Error : ", e.toString());
                }
                device.emit("res_rm", resObj);
            }
        });
        device.on("upload", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject)args[0];
                JSONObject resData = new JSONObject();

                try {

                    String fileName = data.getString("fileName");
                    String path = data.getString("fileDest");
                    String basePath = Environment.getExternalStorageDirectory().getCanonicalPath();
                    ByteArrayOutputStream byteArr = new ByteArrayOutputStream();
                    JSONArray buffer = data.getJSONArray("buffer");

                    File uploadFile = new File(basePath+path+fileName);

                    if(!uploadFile.exists()) uploadFile.createNewFile();

                    FileOutputStream write = new FileOutputStream(uploadFile);

                    for(int i = 0, len = buffer.length(); i<len; i++){
                        byteArr.write(buffer.getInt(i));
                    }
                    byte binData[] = byteArr.toByteArray();
                    write.write(binData);
                    write.close();
                    resData.put("isComplete", true);
                    resData.put("tree", getFileTree(Environment.getExternalStorageDirectory()));
                }catch(Exception e){
                    Log.e("UploadError!", e.toString());
                }
                device.emit("res_upload", resData);
            }
        });
        device.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                sendPowerOffMsg();
            }
        });
    }
    private boolean copyFile(File sourceFile, File newFile){
        FileInputStream in;
        FileOutputStream out;
        File childFile;
        File []list;
        byte []data;
        int len;
        boolean flag = true;
        try {
            if (sourceFile.isFile()) {
                in = new FileInputStream(sourceFile);
                out = new FileOutputStream(newFile);

                data = new byte[in.available()];
                while((len = in.read(data)) != -1){
                    out.write(data,0,len);
                }
                in.close();
                out.close();
                flag = true;
            }else if(sourceFile.isDirectory()){
                if(newFile.mkdir()) {
                    list = sourceFile.listFiles();
                    for(int i = 0; i<list.length && flag; i++){
                        childFile = new File(newFile.getCanonicalPath()+"/"+list[i].getName());
                        flag = copyFile(list[i], childFile);
                    }
                }else flag = false;
            }
        }catch(Exception e){
            Log.e("copy recusion error", e.toString());
            flag = false;
        }
        return flag;
    }
    private JSONArray getFileTree(File root){

        this.cnt = 0;
        JSONArray tree =  new JSONArray();
        JSONObject rootDir = new JSONObject();
        try {
            rootDir.put("id", "root_0");
            rootDir.put("value", "root");
            rootDir.put("open", true);
            rootDir.put("type", "forder");
            rootDir.put("date", root.lastModified()/1000);
            rootDir.put("data", createFileTree(root));
            tree.put(rootDir);
            //id: "files", value: "Files", open: true,  type: "folder", date:  new Date(2014,2,10,16,10), data
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return tree;
//        return createFileTree(root);
    }
    private boolean deleteFile(File root){
        File fileList[];
        boolean flag = true;
        if(root.isDirectory()) {
            fileList = root.listFiles();
            if (fileList.length > 0) {
                for (int i = 0; i < fileList.length; i++) {
                    flag = deleteFile(fileList[i]);
                    if (!flag) break;
                }
            }
        }
        if(flag) flag = root.delete();

        return flag;
    }
    private JSONArray createFileTree(File root){
        File[] childList = root.listFiles();
        String[] nameList = root.list();
//        Log.e("CHECK : ", (nameList== null)+" ");
        JSONArray list = new JSONArray();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd h:mm a");
        Calendar calendar = Calendar.getInstance();
        JSONArray data;
        JSONObject obj;
        File node;
        Long timelong, size;

        int lastPoint, nameLength;
        String executeType, type;
        for(int idx = 0; idx < childList.length; idx++){
            node = childList[idx];
            if(node.isHidden() || (node.getName().toLowerCase().equals("android") && node.isDirectory()) ) continue;
            cnt++;
            try {
                calendar.setTime(new Date(node.lastModified()));

                data = new JSONArray();
                obj = new JSONObject();

                obj.put("id", "file_"+cnt);
//                obj.put("id", node.getName());
//                Log.e("id : ", ""+node.getAbsolutePath()+"/"+node.getName());
                obj.put("value", node.getName());
                obj.put("open", false);
                obj.put("date",node.lastModified()/1000);
                if(node.isDirectory()) {
                    type = "folder";
                    data = createFileTree(node);
                    obj.put("type", type);
                    if(data.length() > 0) {
                        obj.put("data", data);
                    }
                }
                else if (node.isFile()) {
                    lastPoint = node.getName().lastIndexOf(".");
                    nameLength = node.getName().length();
                    size = node.length();
                    obj.put("size", size);

                    if(lastPoint != -1){
                        executeType = node.getName().substring(lastPoint+1, nameLength);
                        executeType = executeType.toLowerCase();
//                        Log.e("executeType", executeType);
                        if(executeType.equals("doc") || executeType.equals("docx") || executeType.equals("docm")){
                            type = "Document";
                        }else if(executeType.equals("dot") || executeType.equals("dotx") || executeType.equals("dotm")){
                            type = "Document";
                        }else if(executeType.equals("ppt") || executeType.equals("pptx") || executeType.equals("pptm")){
                            type = "pp";
                        }else if(executeType.equals("pot") || executeType.equals("potx") || executeType.equals("potm")){
                            type = "pp";
                        }else if(executeType.equals("pps") || executeType.equals("ppsx") || executeType.equals("ppsm")){
                            type = "pp";
                        }else if(executeType.equals("xls") || executeType.equals("xlsx") || executeType.equals("xlsm")){
                            type = "excel";
                        }else if(executeType.equals("zip") || executeType.equals("tar") || executeType.equals("rar")){
                            type = "archive";
                        }else if(executeType.equals("jar") || executeType.equals("alz") || executeType.equals("xlsm")){
                            type = "archive";
                        }else if(executeType.equals("jpg") || executeType.equals("jpeg") || executeType.equals("gif")){
                            type = "image";
                        }else if(executeType.equals("png") || executeType.equals("psd") || executeType.equals("pdd")){
                            type = "image";
                        }else if(executeType.equals("tif") || executeType.equals("raw") || executeType.equals("svg")){
                            type = "image";
                        }else{
                            type = executeType;
                        }
                    }else {
                        type = "file";
                    }
                    obj.put("type", type);
//                id: "files", value: "Files", open: true,  type: "folder", date:  new Date(2014,2,10,16,10), data:
                }
                list.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    class AutoLogin extends Thread{
        public void run(){
            Intent intent;
            while(true) {
                if (device.connected()) {
                    // Check SharedPreference UserInfo
                    if ((userId != null && userPwd != null)) {
                        Log.e("Start Login!", "4");
                        // Auto Login
                        if (login()) intent = new Intent(activity, AnystroageMain.class);
                        else intent = new Intent(activity, LoginActivity.class);
//                        intent = new Intent(activity, LoginActivity.class);
                    } else {
                        intent = new Intent(activity, LoginActivity.class);
                    }
                    // Change Activity
                    activity.startActivity(intent);
                    activity.finish();
                    break;
                }
                try{
                    Thread.sleep(500);
                }catch(Exception e){}
            }

        }
    }
}
