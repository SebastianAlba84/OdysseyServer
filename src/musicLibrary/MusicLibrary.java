package musicLibrary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Scanner;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;

import org.apache.commons.io.FileUtils;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.Mp3File;

import Sorts.Sort;
import treeStructure.AVLTree;
import treeStructure.BTree;
import treeStructure.BinarySearchTree;
import treeStructure.SplayTree;
/**
 * Clase encargada de gestionar la biblioteca musical del usuario y general
 * @author Sebastian Alba
 * @author David Pereira
 * @author Randall Mendez
 *
 */
public class MusicLibrary {
	private JsonObjectBuilder objBuilder;
	private JsonArrayBuilder arrBuilder;
	private JsonArray finalArray;
	
	private String home = System.getProperty("user.home"); //Obtiene la ruta principal del sistema (C://user//xxxx//)
	private String folderPath = home + "\\Documents\\MusicLibrary\\"; //Ruta donde se almacenaran las canciones
	/**
	 * Constructor de la clase
	 * @throws Exception
	 */
	public MusicLibrary() throws Exception {
		this.objBuilder = Json.createObjectBuilder();
		this.arrBuilder = Json.createArrayBuilder();
		
		File folder = new File(folderPath + "Principal");
		if(!folder.exists()) { //Crea la carpeta en caso de que no exista
			folder.mkdirs();
		}
	}
	/**
	 * Metodo para guardar canciones cargadas desde el cliente
	 * @param songName NOMBRE DE CANCION	
	 * @param buf     BYTES DE LA CANCION
	 * @param userName   USUARIO EN SESION
	 * @throws Exception
	 */
	public void storeSong(String songName, byte[] buf, String userName) throws Exception{
		File song = new File(folderPath + "Principal\\" + songName); //Crea el archivo donde se guardara la cancion
		File userSong = new File(folderPath + userName + "\\" + songName + ".mp3");
		if(!song.exists()) { //Comprueba si la cancion ya existe en el directorio
			FileUtils.writeByteArrayToFile(song, buf);
			Files.copy(song.toPath(), userSong.toPath()); //Copia la cancion en la biblioteca especifica del usuario
			this.saveMetadata(song, userSong, userName); //Llama al metodo para guardar la metadata de la cancion
		}else {
			System.out.println("ERROR: La cancion ya se encuentra en la biblioteca.");
			return;
		}		
	}
	/**
	 * Se encarga de actualizar la metadata de las canciones que contiene el usuario en su biblioteca propia.
	 * @param song
	 * @param userName
	 * @param metadata
	 * @throws Exception
	 * @throws IllegalArgumentException
	 */
	public void updateMetadata(File song, String userName, String[] metadata) throws Exception, IllegalArgumentException {
		Mp3File mp3File = new Mp3File(song);
		ID3v2 tag = mp3File.getId3v2Tag();
		String oldTitle = null;
		
		boolean change = false;
		if(!tag.getTitle().equalsIgnoreCase(metadata[0]) && metadata[0] != "") {
			oldTitle = tag.getTitle();
			tag.setTitle(metadata[0]);	
			change = true;
		}
		if(!tag.getArtist().equalsIgnoreCase(metadata[1]) && metadata[1] != "") {
			tag.setArtist(metadata[1]);
			change = true;
		}
		if(!tag.getGenreDescription().equalsIgnoreCase(metadata[2]) && metadata[2] != "") {
			tag.setGenreDescription(metadata[2]);
			change = true;
		}
		if((tag.getAlbum() == null || !tag.getAlbum().equalsIgnoreCase(metadata[3])) && metadata[3] != "") {
			tag.setAlbum(metadata[3]);
			change = true;
		}
		if(!tag.getYear().equalsIgnoreCase(metadata[4]) && metadata[4] != "") {
			tag.setYear(metadata[4]);
			change = true;
		}
		if(!tag.getLyrics().equalsIgnoreCase(metadata[5]) && metadata[5] != "") {
			tag.setLyrics(metadata[5]);
			change = true;
		}
		
		if(change == true) {
			tag.setPadding(true);
			mp3File.save(mp3File.getFilename() + ".retag");
			renameFiles(mp3File);
			if(oldTitle != null) {
				editJsonDoc(oldTitle, tag, userName);
				mp3File.save(folderPath + userName + "\\" + metadata[0] + ".mp3");
				Files.deleteIfExists(song.toPath());
			}else {
				editJsonDoc(tag.getTitle(), tag, userName);
			}
		}
	}
	/**
	 * Metodo para sincronizar la metadata con el proveedor externo, utilizando el API MUSICXMATCH, la cual provee la letra de la cancion.
	 * @param song
	 * @param userName
	 * @throws Exception
	 */
	@SuppressWarnings("resource")
	public void syncMetadata(File song, String userName) throws Exception {
		if(song.exists()) {	
			Mp3File mp3 = new Mp3File(song);
			ID3v2 tag = mp3.getId3v2Tag();
			
			//Realiza la conexion al proveedor
			String titleTag = tag.getTitle().replaceAll(" ", "%20");
			String artistTag = tag.getArtist().replaceAll(" ", "%20");
			String request = "http://api.musixmatch.com/ws/1.1/track.search?apikey=0a181cdc1ba6d0a216b779cf8ad585df&q_track=" + titleTag +"&q_artist=" + artistTag + "&page_size=1&s_track_rating=desc";
			URL url = new URL(request);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			
			InputStream IS = connection.getInputStream();	//
			Scanner s = new Scanner(IS).useDelimiter("\\A");// Obtiene la respuesta del proveedor y la convierte a un String
			String result = s.hasNext() ? s.next() : "";	//
			
			JsonReader jsonReader = Json.createReader(new StringReader(result)); // 
			JsonObject jsonObj = jsonReader.readObject();						 // Parsea el string a JsonObject		
			jsonReader.close();													 //
			
			JsonObject track = (JsonObject) jsonObj.get("message").asJsonObject().get("body").asJsonObject().get("track_list").asJsonArray().get(0).asJsonObject().get("track");
			int trackID = track.getInt("track_id");
			String trackName, artist, genre, album, year, lyrics;
			try{
				trackName = track.getString("track_name");
			}catch(Exception ex){
				trackName = tag.getTitle();
			}
			try {
				artist = track.getString("artist_name");
			}catch(Exception ex) {
				artist = tag.getArtist();
			}
			try {
				JsonArray genreList = (JsonArray) track.get("primary_genres").asJsonObject().get("music_genre_list");
				genre = genreList.get(0).asJsonObject().get("music_genre").asJsonObject().getString("music_genre_name");
				tag.setGenreDescription(genre);
			}catch(Exception ex) {
				genre = tag.getGenreDescription();
			}
			try {
				album = track.getString("album_name");
			}catch(Exception ex) {
				if(tag.getAlbum() != null) {
					album = tag.getAlbum();
				}else {
					album = tag.getTitle();
				}
			}
			try {
				year = track.getString("first_release_date");
				year = year.substring(0, 4);
			}catch(Exception ex) {
				year = tag.getYear();
			}
			try {
				lyrics = getSongLyrics(trackID);
			}catch(Exception ex) {
				lyrics = "Unknown";
			}
			
			String[] arr = {trackName, artist, genre, album, year, lyrics};
			updateMetadata(song, userName, arr);
		}else {
			System.out.println("ERROR: La cancion no se encuentra en la biblioteca.");
		}
	}
	/**
	 * Metodo encargado de borrar las canciones en la biblioteca del usuaio, en la propia.
	 * @param songTitle
	 * @param userName
	 * @throws Exception
	 */
	public void deleteSong(String songTitle, String userName) throws Exception{
		File file = new File(folderPath + userName + "\\" + songTitle + ".mp3");
		if(file.exists()) {
			Files.delete(file.toPath());
			try {
				FileReader fileReader = new FileReader(folderPath + userName + "\\" + "MusicLibrary.json");
				if(fileReader.ready()) {
					InputStream tempIS = new FileInputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));			
					JsonReader reader = Json.createReader(tempIS);			
					JsonArray array = reader.readArray();			
					reader.close();
					for(int i = 0; i < array.size(); i++) {
						JsonObject obj = array.getJsonObject(i);
						if(!obj.getString("Title").equalsIgnoreCase(songTitle)) {
							arrBuilder.add(obj);
						}
					}
					finalArray = arrBuilder.build();
					OutputStream tempOS = new FileOutputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));
					JsonWriter writer = Json.createWriter(tempOS);
					writer.writeArray(finalArray);
					writer.close();
				}
				fileReader.close();
			}catch(Exception ex) {}
		}else {
			System.out.println("La cancion no se encuentra en la biblioteca.");
		}
	}
	/**
	 * Se encarga de ordenar la biblioteca propia del usuario por titulo de la cancion, utilizando QuickSort
	 * @param userName
	 * @return string 
	 * @throws Exception
	 */
	public String sortLibraryByTitle(String userName) throws Exception {
		try {
			String[] songs = null;
			String sortedLib = "";
			FileReader fileReader = new FileReader(folderPath + userName + "\\" + "MusicLibrary.json");
			if(fileReader.ready()) {
				InputStream tempIS = new FileInputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));			
				JsonReader reader = Json.createReader(tempIS);			
				JsonArray array = reader.readArray();			
				reader.close();
				songs = new String[array.size()];
				for(int i = 0; i < array.size(); i++) {
					JsonObject obj = array.getJsonObject(i);
					songs[i] = obj.getString("Title");
				}
			}
			fileReader.close();
			
			if(songs!=null) {
				Sort sort = new Sort();
				songs = sort.quickSort(songs);
				sortedLib = "";
				for (int i = 0; i < songs.length; i++) {
					sortedLib += songs[i] + "/";
				}
			}
			return sortedLib;
		}catch(Exception ex){
			return "false";
		}
	}
	/**
	 * Se encarga de ordenar la biblioteca propia del usario, por nombre de artista, usando RadixSort
	 * @param userName
	 * @return string
	 * @throws Exception
	 */
	public String sortLibraryByArtist(String userName) throws Exception {
		try {
			String[] artists = null;
			JsonArray array = null;
			String sortedLib = "";
			FileReader fileReader = new FileReader(folderPath + userName + "\\" + "MusicLibrary.json");
			if(fileReader.ready()) {
				InputStream tempIS = new FileInputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));			
				JsonReader reader = Json.createReader(tempIS);			
				array = reader.readArray();			
				reader.close();
				artists = new String[array.size()];
				for(int i = 0; i < array.size(); i++) {
					JsonObject obj = array.getJsonObject(i);
					String artist = obj.getString("Artist");
					boolean bool = false;
					for (int j = 0; j < artists.length; j++) {
						if(artist.equalsIgnoreCase(artists[j])) {
							bool = true;
						}
					}
					if (!bool) {
						artists[i] = artist;
					}else {
						artists[i] = "";
					}
				}
			}
			fileReader.close();
			
			if(artists != null && array != null) {
				Sort sort = new Sort();
				artists = sort.radixSort(artists);
				sortedLib = "";
				for (int i = 0; i < artists.length; i++) {
					if(artists[i]!="") {
						String artist = artists[i];
						sortedLib += artist + "/";
					
						for (int j = 0; j < array.size(); j++) {
							JsonObject obj = array.getJsonObject(j);
							if(obj.getString("Artist").equals(artist)) {
								sortedLib += obj.getString("Title") + ",";
							}
						}
					}
				}
			}		
			return sortedLib;
		}catch(Exception ex) {
			ex.printStackTrace();
			return "false";
		}
	}
	/**
	 * Se encarga de ordenar la biblioteca propia del usuario por nombre de album, usando Bubble sort
	 * @param userName
	 * @return string
	 * @throws Exception
	 */
	public String sortLibraryByAlbum(String userName) throws Exception {
		try {	
			String[] albums = null;
			JsonArray array = null;
			String sortedLib = "";
			FileReader fileReader = new FileReader(folderPath + userName + "\\" + "MusicLibrary.json");
			if(fileReader.ready()) {
				InputStream tempIS = new FileInputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));			
				JsonReader reader = Json.createReader(tempIS);			
				array = reader.readArray();			
				reader.close();
				albums = new String[array.size()];
				for(int i = 0; i < array.size(); i++) {
					JsonObject obj = array.getJsonObject(i);
					String album = obj.getString("Album");
					boolean bool = false;
					for (int j = 0; j < albums.length; j++) {
						if(album.equalsIgnoreCase(albums[j])) {
							bool = true;
						}
					}
					if (!bool) {
						albums[i] = album;
					}else {
						albums[i] = "";
					}
				}
			}
			fileReader.close();
			
			if(albums != null) {
				Sort sort = new Sort();
				albums = sort.bubbleSort(albums);
				sortedLib = "";
				for (int i = 0; i < albums.length; i++) {
					if(albums[i] != "") {
						String album = albums[i];
						sortedLib += album + "/";
						for (int j = 0; j < array.size(); j++) {
							JsonObject obj = array.getJsonObject(j);
							if(obj.getString("Album").equals(album)) {
								sortedLib += obj.getString("Title") + ",";
							}
						}
					}
				}
			}
			return sortedLib;
		}catch(Exception ex) {
			return "false";
		}
	}
	/**
	 * Se encarga de buscar el titulo de la cancion, utilizando el arbol B
	 * @param title
	 * @return songs
	 */
	public String searchByTitle(String title) {
		IndexLibrary index = new IndexLibrary();
		BTree indexTitles = index.createTitleIndex();
		String[] array = indexTitles.traverseTree();
		String songs = "";
		for (int i = 0; i < array.length; i++) {
			String value = array[i];
			for (int j = 0; j <= value.length()-title.length(); j++) {
				if(value.substring(j, j+title.length()).equalsIgnoreCase(title)) {
					songs += value + "/";
				}
			}
		}
		return songs;
	}
	/**
	 * Se encarga de buscar por nombre del artista, utilizando el arbol AVL
	 * Búsqueda de canciones por Artista mediante Arbol tipo AVL.
	 * Indexa el Arbol AVL, que contiene los artistas como llaves, crea un array que guarda una arreglo ordenado de esos artistas, 
	 * recorre ese arreglo en busca del artista que se ingreso, cuando lo encuentra, obtiene sus canciones relacionadas a ese artista,
 	 * los concatena a un storing y eso se retorna
	 * @param artist
	 * @return songs
	 */
	public String searchByArtist(String artist) {
		IndexLibrary index = new IndexLibrary();
		AVLTree indexArtist = index.createArtistIndex();
		String[] array = indexArtist.inorder();
		String songs = "";
		for (int i = 0; i < array.length; i++) {
			System.out.println(array[i]);
			JsonObject obj = Json.createReader(new StringReader(array[i])).readObject();
			if (obj.getString("Artist").equalsIgnoreCase(artist)){					
				songs += obj.getString("Title") + "/";
			}
		}
		
		if(songs != "") {
			return songs;
		}else {
			return "false";
		}	
	}
	/**
	 * Indexa el Arbol Splay, que contiene los Albumes como llaves, crea un array que guarda una arreglo ordenado de esos artistas,
	 *  recorre ese arreglo en busca del Album que se ingreso, cuando lo encuentra, obtiene sus canciones relacionadas a ese Album,
	 * los concatena a un storing y eso se retorna
	 * Se encarga de buscar por nombre de album, utilizando arbol Splay
	 * @param album
	 * @return songs
	 */
	public String searchByAlbum(String album) {
		IndexLibrary index = new IndexLibrary();
		SplayTree indexAlbum = index.createAlbumIndex();
		String[] array = indexAlbum.inorder();
		String songs = "";
		for (int i = 0; i < array.length; i++) {
			JsonObject obj = Json.createReader(new StringReader(array[i])).readObject();
			if (obj.getString("Album").equalsIgnoreCase(album)){
				songs += obj.getString("Title") + "/";
			}
		}
		
		if(songs != "") {
			return songs;
		}else {
			return "false";
		}
		
	}
	/**
	 * Indexa el Arbol Binario de Busqueda, que contiene la letra de la cancion como llave, crea un array ordenado de estos elementos,
	 * lo recorre para buscar fragmento a fragmento en la letra coincidencias con la frase o palabra que ingreso el usaurio. Al encontrarlo,
	 * concatena el titulo de la cancion a el string que se retornara al final de la funcion.
	 * @param lyrics
	 * @return string
	 */
	public String searchByLyrics(String lyrics) {
		IndexLibrary index = new IndexLibrary();	
		BinarySearchTree indexLyrics = index.createLyricsIndex();
		String[] lyricsArr = indexLyrics.inorder();
		String songs = "";
		for (int i = 0; i < lyricsArr.length; i++) {
			String value = lyricsArr[i];
			for (int j = 0; j <= value.length()-lyrics.length(); j++) {
				if(value.substring(j, j+lyrics.length()).equalsIgnoreCase(lyrics)) {
					JsonObject obj = Json.createReader(new StringReader(value)).readObject();
					String val = obj.getString("Title");
					songs += val + "/";
					break;
				}
			}
		}
		return songs;
	}
	
	/**
	 * Se encarga de obtener la lista de canciones del usuario en su biblioteca
	 * @param userName
	 * @return songs
	 * @throws Exception
	 */
	public String getUserLibrary(String userName) throws Exception {
		String songs = "";
		FileReader fileReader = new FileReader(folderPath + userName + "\\" + "MusicLibrary.json");
		if(fileReader.ready()) {
			InputStream tempIS = new FileInputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));			
			JsonReader reader = Json.createReader(tempIS);			
			JsonArray array = reader.readArray();			
			reader.close();
			for(int i = 0; i < array.size(); i++) {
				JsonObject obj = array.getJsonObject(i);
				songs += obj.getString("Title") + "/";
			}
		}
		fileReader.close();
		return songs;
	}
	
	public void addRating(String songTitle, int newVote) {
		
	}
	/**
	 * Se encarga de obtener las letras de las canciones, haciendo la consulta al proveedor externo de la metadata
	 * @param trackID
	 * @return lyrics
	 * @throws Exception
	 */
	@SuppressWarnings("resource")
	private String getSongLyrics (int trackID) throws Exception {
		String request = "http://api.musixmatch.com/ws/1.1/track.lyrics.get?track_id=" + trackID + "&apikey=0a181cdc1ba6d0a216b779cf8ad585df";
		URL url = new URL(request);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		
		InputStream IS = connection.getInputStream();	//
		Scanner s = new Scanner(IS).useDelimiter("\\A");// Obtiene la respuesta del proveedor y la convierte a un String
		String result = s.hasNext() ? s.next() : "";	//
		
		JsonReader jsonReader = Json.createReader(new StringReader(result)); // 
		JsonObject jsonObj = jsonReader.readObject();						 // Parsea el string a JsonObject		
		jsonReader.close();													 //
		
		String lyrics = jsonObj.get("message").asJsonObject().get("body").asJsonObject().get("lyrics").asJsonObject().getString("lyrics_body");
		return lyrics;
	}
	/**
	 * Se encarga de guardar la metadata en un archivo musical mp3 y en el JSON
	 * @param song
	 * @param userSong
	 * @param userName
	 * @throws Exception
	 */
	private void saveMetadata(File song, File userSong, String userName) throws Exception {
		Mp3File mp3File = new Mp3File(song);
		
		ID3v2 tag;
		if(mp3File.hasId3v2Tag()) {
			tag = mp3File.getId3v2Tag();
		}else {
			tag = new ID3v24Tag();
			mp3File.setId3v2Tag(tag);
		}
		
		try {
			FileReader fileReader = new FileReader(folderPath + "Principal\\" + "MusicLibrary.json");
			if(fileReader.ready()) {
				InputStream tempIS = new FileInputStream(new File(folderPath + "Principal\\" + "MusicLibrary.json"));			
				JsonReader reader = Json.createReader(tempIS);			
				JsonArray oldArray = reader.readArray();			
				reader.close();			
				for(JsonValue i:oldArray) {
					arrBuilder.add(i);
				}
			}
			fileReader.close();
		}catch(FileNotFoundException ex) {}
		
		boolean change = false;
		try{
			objBuilder.add("Title", tag.getTitle()).toString();
		}catch(NullPointerException ex) {
			objBuilder.add("Title", "Unavailable");
			tag.setTitle("Unavailable");
			change = true;
		}
		try{
			objBuilder.add("Artist", tag.getArtist()).toString();
		}catch(NullPointerException ex) {
			objBuilder.add("Artist", "Unavailable");
			tag.setArtist("Unavailable");
			change = true;
		}
		try{
			objBuilder.add("Genre", tag.getGenreDescription()).toString();
		}catch(NullPointerException ex) {
			objBuilder.add("Genre", "Other");
			tag.setGenreDescription("Other");
			change = true;		
		}
		try{
			objBuilder.add("Album", tag.getAlbum()).toString();
		}catch(NullPointerException ex) {
			objBuilder.add("Album", "Unavailable");
			tag.setAlbum("Unavailable");
			change = true;
		}
		try{
			objBuilder.add("Year", tag.getYear()).toString();
		}catch(NullPointerException ex) {
			objBuilder.add("Year", "Unavailable");
			tag.setYear("Unavailable");
			change = true;
		}
		try{
			objBuilder.add("Lyrics", tag.getLyrics()).toString();
		}catch(NullPointerException ex) {
			objBuilder.add("Lyrics", "Unavailable");
			tag.setLyrics("Unavailable");
			change = true;
		}
		
		if(change) {
			tag.setPadding(true);
			mp3File.save(mp3File.getFilename() + ".retag");
			renameFiles(mp3File);
			Files.deleteIfExists(userSong.toPath());
			Files.copy(song.toPath(), userSong.toPath());
		}
		
		OutputStream OS = new FileOutputStream(folderPath + "Principal\\" + "MusicLibrary.json");
		JsonObject obj = objBuilder.build();
		arrBuilder.add(obj);
		finalArray = arrBuilder.build();
		JsonWriter writer = Json.createWriter(OS);
		writer.writeArray(finalArray);
		writer.close();
		
		//Copia el archivo json al directorio del usuario 
		File newFile = new File(folderPath + userName + "\\" + "MusicLibrary.json");
		File file = new File(folderPath + "Principal\\" + "MusicLibrary.json");
		Files.deleteIfExists(newFile.toPath());
		Files.copy(file.toPath(), newFile.toPath());
	}
	/**
	 * Se encarga de escribir la nueva informacion de la metadata en el archivo JSOn de las canciones
	 * @param title
	 * @param tag
	 * @param userName
	 * @throws Exception
	 */
	private void editJsonDoc(String title, ID3v2 tag, String userName) throws Exception {
		try {
			FileReader fileReader = new FileReader(folderPath + userName + "\\" + "MusicLibrary.json");
			if(fileReader.ready()) {
				InputStream tempIS = new FileInputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));			
				JsonReader reader = Json.createReader(tempIS);			
				JsonArray array = reader.readArray();			
				reader.close();
				for(int i = 0; i < array.size(); i++) {
					JsonObject obj = array.getJsonObject(i);
					if(obj.getString("Title").equalsIgnoreCase(title)) {
						objBuilder.add("Title", tag.getTitle());
						objBuilder.add("Artist", tag.getArtist());
						objBuilder.add("Genre", tag.getGenreDescription());
						objBuilder.add("Album", tag.getAlbum());
						objBuilder.add("Year", tag.getYear());
						objBuilder.add("Lyrics", tag.getLyrics());
						JsonObject newObj = objBuilder.build();
						
						for(int j = 0; j < array.size(); j++) {
							if(j == i) {
								arrBuilder.add(newObj);
							}else {
								arrBuilder.add(array.get(j));
							}
						}
						finalArray = arrBuilder.build();
						OutputStream tempOS = new FileOutputStream(new File(folderPath + userName + "\\" + "MusicLibrary.json"));
						JsonWriter writer = Json.createWriter(tempOS);
						writer.writeArray(finalArray);
						writer.close();
						break;
					}	
				}
				
			}
			fileReader.close();
		}catch(FileNotFoundException ex) {}
	}
	/**
	 * Se encarga de renombrar archivos
	 * @param mp3File
	 */
	private void renameFiles(Mp3File mp3File) {
		File originalFile = new File(mp3File.getFilename());
		File backupFile = new File(mp3File.getFilename() + ".bak");
		File retaggedFile = new File(mp3File.getFilename() + ".retag");
		
		originalFile.renameTo(backupFile);
		retaggedFile.renameTo(originalFile);
		backupFile.delete();
	}
}

