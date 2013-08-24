/*
 * Ignore this package.
 * It's for Slipstream/GMM catalog maintenance.
 */

package net.vhati.modmanager.scraper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.vhati.ftldat.FTLDat;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.json.JacksonGrognakCatalogReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ForumScraper {

	private static final Logger log = LogManager.getLogger(ForumScraper.class);

	private static final String MASTER_LIST_URL = "http://www.ftlgame.com/forum/viewtopic.php?f=11&t=2645";
	private static final String FORUM_URL_FRAGMENT = "http://www.ftlgame.com/forum/viewtopic.php";


	public static void main( String[] args ) {

		List<String> ignoredURLs = new ArrayList<String>();
		ignoredURLs.add( "http://www.ftlgame.com/forum/viewtopic.php?f=11&t=11561" );
		ignoredURLs.add( "http://www.ftlgame.com/forum/viewtopic.php?f=12&t=11083" );
		ignoredURLs.add( "http://www.ftlgame.com/forum/viewtopic.php?f=4&t=2938" );
		ignoredURLs.add( "http://www.moddb.com/mods/better-planets-and-backgrounds/downloads/better-asteroids" );
		// SpaceDock is an app.
		ignoredURLs.add( "http://www.ftlgame.com/forum/viewtopic.php?f=11&t=16842" );
		// Beginning Scrap Advantage is bundled in GMM.
		ignoredURLs.add( "http://www.ftlgame.com/forum/viewtopic.php?f=4&t=2464" );

		List<Option> options = new ArrayList<Option>();
		options.add( new Option( "load-json",   "--load-json",   null, true, true ) );
		options.add( new Option( "load-xml",    "--load-xml",    null, true, true ) );
		options.add( new Option( "scrape",      "--scrape",      null, true, true ) );
		options.add( new Option( "dump-json",   "--dump-json",   null, true, true ) );
		options.add( new Option( "dump-xml",    "--dump-xml",    null, true, true ) );
		options.add( new Option( "hash-thread", "--hash-thread", null, true, true ) );
		options.add( new Option( "first-post",  "--first-post",  null, true, true ) );
		options.add( new Option( "help",        "--help",        "-h", true, true ) );

		List<String[]> tasks = parseArgs( args, options );

		if ( tasks.isEmpty() )
			tasks.add( new String[] {"help"} );

		ModDB modDB = new ModDB();

		try {
			for ( String[] task : tasks ) {
				if ( task[0].equals( "load-json" ) ) {
					log.info( "Loading json catalog..." );
					ModDB newDB = JacksonGrognakCatalogReader.parse( new File(task[1]) );
					if ( newDB != null ) modDB = newDB;
				}
				else if ( task[0].equals( "load-xml" ) ) {
					log.info( "Loading xml catalog..." );
					ModDB newDB = parseCatalogXML( new File(task[1]) );
					if ( newDB != null ) modDB = newDB;
				}
				else if ( task[0].equals( "scrape" ) ) {
					log.info( "Scraping..." );
					List<ModsInfo> data = scrape( modDB, MASTER_LIST_URL, ignoredURLs );
					if ( data.size() > 0 ) writeXML( data, new File(task[1]) );
				}
				else if ( task[0].equals( "dump-json" ) ) {
					log.info( "Dumping json..." );
					List<ModsInfo> data = getCollatedModInfo( modDB );
					if ( data.size() > 0 ) writeJSON( data, new File(task[1]) );
				}
				else if ( task[0].equals( "dump-xml" ) ) {
					log.info( "Dumping xml..." );
					List<ModsInfo> data = getCollatedModInfo( modDB );
					if ( data.size() > 0 ) writeXML( data, new File(task[1]) );
				}
				else if ( task[0].equals( "hash-thread" ) ) {
					log.info( "Hashing thread..." );
					System.out.println( hashThread( task[1] ) );
				}
				else if ( task[0].equals( "first-post" ) ) {
					log.info( "Getting thread's first post..." );
					System.out.println( getFirstPost( task[1] ) );
				}
				else if ( task[0].equals( "help" ) ) {
					System.out.println( "Usage: java -cp modman.jar net.vhati.modmanager.scraper.ForumScraper [OPTION]" );
					System.out.println( "" );
					System.out.println( "Load an existing catalog as the moddb, and scrape." );
					System.out.println( "Edit the catalog by copy/pasting scrape snippets." );
					System.out.println( "Load the edited catalog and dump json." );
					System.out.println( "" );
					System.out.println( "      --load-json FILE   load moddb from a json GMM catalog" );
					System.out.println( "      --load-xml FILE    load moddb from an xml file" );
					System.out.println( "      --scrape FILE      write changed forum posts to an xml file" );
					System.out.println( "      --dump-json FILE   write the moddb to a json file" );
					System.out.println( "      --dump-xml FILE    write the moddb to an xml file" );
					System.out.println( "" );
					System.out.println( "      --hash-thread URL  print the hash of a specific thread" );
					System.out.println( "      --first-post URL   print the first post of a thread (debugging)" );
					System.out.println( "" );
					System.out.println( "  -h, --help             display this help and exit" );
					System.out.println( "" );
					System.out.println( "" );
				}
			}
		}
		catch ( Exception e ) {
			log.error( "An error occurred.", e );
		}
	}


	/**
	 * I want to take args, but not badly enough for another library. :P
	 */
	private static List<String[]> parseArgs( String[] args, List<Option> options ) {
		List<String[]> tasks = new ArrayList<String[]>();

		for ( int i=0; i < args.length; ) {
			boolean foundOption = false;

			for ( Option opt : options ) {
				String[] result = null;
				boolean matches = false;
				if ( opt.shortName != null && opt.shortName.equals(args[i]) ) matches = true;
				if ( opt.longName != null && opt.longName.equals(args[i]) ) matches = true;
				if ( matches ) {
					foundOption = true;
					if ( opt.acceptsValue ) {
						result = new String[] {opt.action, null};

						if ( i+1 < args.length && !args[i+1].startsWith("-") ) {
							result[1] = args[i+1];
							i += 2;
						} else if ( opt.requiresValue ) {
							log.error( String.format( "The \"%s\" option requires an argument.", args[i] ) );
							i++;
						} else {
							i++;
						}
						tasks.add( result );
					}
					else {
						result = new String[] {opt.action};
						tasks.add( result );
						i++;
					}
				}
				if ( foundOption ) break;
			}
			if ( !foundOption ) {
				log.info( "Unrecognized option: "+ args[i] );
				i++;
			}
		}
		return tasks;
	}


	/**
	 * Collects ModInfo objects that differ only in version, and creates ModsInfo objects.
	 */
	private static List<ModsInfo> getCollatedModInfo( ModDB modDB ) {
		List<ModsInfo> results = new ArrayList<ModsInfo>();
		List<ModInfo> seenList = new ArrayList<ModInfo>();

		for ( ModInfo modInfo : modDB.getCatalog() ) {
			if ( seenList.contains( modInfo ) ) continue;
			seenList.add( modInfo );

			ModsInfo modsInfo = new ModsInfo();
			modsInfo.title = modInfo.getTitle();
			modsInfo.author = modInfo.getAuthor();
			modsInfo.threadURL = modInfo.getURL();
			modsInfo.description = modInfo.getDescription();

			String threadHash = modDB.getThreadHash( modInfo.getURL() );
			modsInfo.threadHash = ( threadHash != null ? threadHash : "???" );

			modsInfo.putVersion( modInfo.getFileHash(), modInfo.getVersion() );

			HashMap<String,List<ModInfo>> similarMods = modDB.getSimilarMods( modInfo );
			for ( ModInfo altInfo : similarMods.get( ModDB.EXACT ) ) {
				if ( seenList.contains( altInfo ) ) continue;
				seenList.add( altInfo );

				modsInfo.putVersion( altInfo.getFileHash(), altInfo.getVersion() );
			}

			results.add( modsInfo );
		}

		return results;
	}


	/**
	 * Scrapes the forum for changed posts and returns info from updated mods.
	 */
	private static List<ModsInfo> scrape( ModDB knownDB, String masterListURL, List<String> ignoredURLs ) throws IOException, NoSuchAlgorithmException {
		List<ModsInfo> results = new ArrayList<ModsInfo>();

		List<ScrapeResult> scrapeList = scrapeMasterList( knownDB, masterListURL, ignoredURLs );

		for ( ScrapeResult scrapedInfo : scrapeList ) {
			ModsInfo modsInfo = new ModsInfo();
			modsInfo.title = scrapedInfo.title;
			modsInfo.author = scrapedInfo.author;
			modsInfo.threadURL = scrapedInfo.threadURL;
			modsInfo.threadHash = scrapedInfo.threadHash;
			modsInfo.description = scrapedInfo.rawDesc;
			modsInfo.putVersion( "???", "???"+ (scrapedInfo.wip ? " WIP" : "") );
			results.add( modsInfo );
		}

		return results;
	}


	/**
	 * Scrape the Master Mod List on the FTL forum.
	 *
	 * If an existing ModDB is provided, its thread urls will be checked too.
	 *
	 * @param knownDB a ModDB with mods to ignore if threadHash is unchanged
	 * @param ignoredUrls a list of uninteresting threadURLs to ignore
	 */
	private static List<ScrapeResult> scrapeMasterList( ModDB knownDB, String masterListURL, List<String> ignoredURLs ) throws IOException, NoSuchAlgorithmException {
		if ( ignoredURLs == null ) ignoredURLs = new ArrayList<String>();

		Pattern modsHeaderPtn = Pattern.compile( Pattern.quote("<span style=\"font-weight: bold\"><span style=\"text-decoration: underline\"><span style=\"font-size: 150%; line-height: 116%;\">Mods</span></span></span>") );
		Pattern modPtn = Pattern.compile( "^(?:\\[[A-Za-z0-9 ]+ *\\])?<a href=\"([^\"]+)\"[^>]*>([^>]+)</a> *((?:\\[[A-Za-z0-9 ]+\\])?)(?: (?:.*?))? - Author: <a href=\"[^\"]+\"[^>]*>([^<]+?)</a>" );

		HashSet<String> boringHashes = new HashSet<String>();
		if ( knownDB != null ) {
			for ( ModInfo modInfo : knownDB.getCatalog() ) {
				String threadHash = knownDB.getThreadHash( modInfo.getURL() );
				if ( threadHash == null ) {
					log.debug( "No thread hash for modInfo: "+ modInfo.getTitle() );
				}
				if ( threadHash != null && !threadHash.equals("???") )
					boringHashes.add( threadHash );
			}
		}

		String postContent = getFirstPost( masterListURL );
		postContent = postContent.replaceAll( "<br */>", "\n" );

		String[] lines = postContent.split("\n");
		List<ScrapeResult> results = new ArrayList<ScrapeResult>();
		List<String> pendingURLs = new ArrayList<String>();
		boolean inMods = false;
		Matcher m = null;

		for ( String line : lines ) {
			if ( modsHeaderPtn.matcher(line).find() ) {
				inMods = true;
				continue;
			}
			if ( !inMods ) continue;

			m = modPtn.matcher(line);
			if ( m.find() ) {
				ScrapeResult result = new ScrapeResult();
				result.threadURL = m.group(1);
				result.title = m.group(2);
				result.author = m.group(4);
				result.wip = m.group(3).equals("[WIP]");
				result.rawDesc = "";
				result.threadHash = "???";

				result.title = result.title.replaceAll( "&amp;", "&" );
				result.threadURL = result.threadURL.replaceAll( "&amp;", "&" );
				results.add( result );
			}
		}
		if ( knownDB != null ) {
			for ( ScrapeResult result : results ) {
				pendingURLs.add( result.threadURL );
			}
			for ( ModInfo modInfo : knownDB.getCatalog() ) {
				if ( !modInfo.getURL().equals("???") && !pendingURLs.contains(modInfo.getURL()) ) {
					pendingURLs.add( modInfo.getURL() );
					ScrapeResult result = new ScrapeResult();
					result.threadURL = modInfo.getURL();
					result.title = modInfo.getTitle();
					result.author = modInfo.getAuthor();
					result.wip = false;  // *shrug*
					result.rawDesc = modInfo.getDescription();
					result.threadHash = knownDB.getThreadHash( modInfo.getURL() );
					results.add( result );
				}
			}
		}

		// Prune results with boring urls.
		for ( Iterator<ScrapeResult> it=results.iterator(); it.hasNext(); ) {
			ScrapeResult result = it.next();
			if ( ignoredURLs.contains( result.threadURL ) )
				it.remove();
		}

		// Fetch and hash each thread url.
		for ( int i=0; i < results.size(); i++ ) {
			ScrapeResult result = results.get(i);
			if ( result.threadURL.startsWith( FORUM_URL_FRAGMENT ) == false )
				continue;  // Don't bother scraping and hashing non-forum urls.

			try {Thread.sleep( 2000 );}
			catch ( InterruptedException e ) {log.info( "Inter-fetch sleep interrupted." );}

			log.info( "" );
			log.info( String.format( "Scraping mod %03d/%03d (%s)...", (i+1), results.size(), result.title ) );
			while( true ) {
				try {
					result.rawDesc = getFirstPost( result.threadURL );
					result.threadHash = FTLDat.calcStreamMD5( new ByteArrayInputStream( result.rawDesc.getBytes( Charset.forName("UTF-8") ) ) );
					break;
				}
				catch ( IOException e ) {
					log.error( "Request failed: "+ e.getMessage() );
				}
				try {Thread.sleep( 5000 );}
				catch ( InterruptedException e ) {log.info( "Re-fetch sleep interrupted." );}
			}
		}

		// Ignore threads whose hashes haven't changed.
		for ( Iterator<ScrapeResult> it=results.iterator(); it.hasNext(); ) {
			ScrapeResult result = it.next();
			if ( boringHashes.contains( result.threadHash ) )
				it.remove();
		}

		// Scrub html out of descriptions and scrape download links.
		for ( ScrapeResult result : results ) {
			postContent = result.rawDesc;
			postContent = postContent.replaceAll( "<br */>", "\n" );
			postContent = postContent.replaceAll( "<img [^>]*/>", "" );
			postContent = postContent.replaceAll( "<span [^>]*>", "" );
			postContent = postContent.replaceAll( "</span>", "" );
			postContent = postContent.replaceAll( "&quot;", "\"" );
			postContent = postContent.replaceAll( "\u2018|\u2019", "'" );
			postContent = postContent.replaceAll( "\u2022", "-" );
			postContent = postContent.replaceAll( "\u2013", "-" );
			postContent = postContent.replaceAll( "\u00a9", "()" );
			postContent = postContent.replaceAll( "&amp;", "&" );
			postContent = postContent.replaceAll( "<a (?:[^>]+ )?href=\"([^\"]+)\"[^>]*>", "<a href=\"$1\">" );
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/memberlist.php[^\"]+\"[^>]*>([^<]+)</a>", "$1" );
			postContent = postContent.replaceAll( "<a href=\"http://(?:i.imgur.com/|[^\"]*photobucket.com/|[^\"]*deviantart.com/|www.mediafire.com/view/[?])[^\"]+\"[^>]*>([^<]+)</a>", "$1" );
			postContent = postContent.replaceAll( "<a href=\"([^\"]+)\"[^>]*>(?:\\1|[^<]+ [.][.][.] [^<]+)</a>", "<a href=\"$1\">Link</a>" );
			postContent = postContent.replaceAll( "<a href=\"[^\"]+[.](?:jpg|png)(?:[.]html)?\"[^>]*>([^<]*)</a>", "$1" );
			postContent = postContent.replaceAll( "</li><li>", "</li>\n<li>" );
			postContent = postContent.replaceAll( "<li>(.*?)</li>", " - $1" );
			postContent = postContent.replaceAll( "</li>", "" );
			postContent = postContent.replaceAll( "</?ul>", "" );
			postContent = postContent.replaceAll( "(?s)<blockquote [^>]+><div>(.*?)</div></blockquote>", "<blockquote>$1</blockquote>" );
			postContent = postContent.replaceAll( "<!-- [^>]+ -->", "" );

			// Link to GMM Thread.
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/viewtopic.php?(?:[^&]+&)*t=2464\"[^>]*>([^<]+)</a>", "$1" );
			// Link to Superluminal Thread.
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/viewtopic.php?(?:[^&]+&)*t=11251\"[^>]*>([^<]+)</a>", "$1" );
			// Link to FTLEdit Thread.
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/viewtopic.php?(?:[^&]+&)*t=2959\"[^>]*>([^<]+)</a>", "$1" );

			postContent = postContent.replaceAll( "\\A\\s+", "" );
			postContent = postContent.replaceAll( "\\s+\\Z", "" );
			result.rawDesc = postContent +"\n";  // Raw quoting looks better with a newline.
		}

		return results;
	}


	/**
	 * Extracts the html content of the first post in a forum thread.
	 */
	private static String getFirstPost( String url ) throws IOException {
		String htmlSrc = fetchWebPage( url );

		Pattern firstPostPtn = Pattern.compile( "(?s)<div class=\"postbody\"[^>]*>.*?<div class=\"content\"[^>]*>(.*?)</div>\\s*<dl class=\"postprofile\"[^>]*>" );
		Matcher m = null;

		String postContent = "";
		m = firstPostPtn.matcher( htmlSrc );
		if ( m.find() ) {
			postContent = m.group( 1 );
			postContent = postContent.replaceAll( "\r?\n", "" );

			// Within content, but it counts clicks/views, which throws off hashing.
			postContent = postContent.replaceAll( "(?s)<div class=\"inline-attachment\">.*?</div>", "" );

			// Footer junk.
			//postContent = postContent.replaceAll( "(?s)<dl class=\"attachbox\">.*?<dl class=\"file\">.*?</dl>.*?</dl>", "" );
			postContent = postContent.replaceAll( "(?s)<dl class=\"file\">.*?</dl>", "" );
			postContent = postContent.replaceAll( "(?s)<dd>\\s*?</dd>", "" );
			postContent = postContent.replaceAll( "(?s)<dl class=\"attachbox\">.*?</dl>", "" );
			postContent = postContent.replaceAll( "(?s)<div (?:[^>]+ )?class=\"notice\">.*?</div>", "" );
			postContent = postContent.replaceAll( "(?s)<div (?:[^>]+ )?class=\"signature\">.*?</div>", "" );
			postContent = postContent.replaceAll( "</div>\\s*\\Z", "" );
			postContent = postContent.replaceAll( "\\A\\s+", "" );
			postContent = postContent.replaceAll( "\\s+\\Z", "" );
		}

		return postContent;
	}


	/**
	 * Calculates an MD5 hash of the first post in a thread.
	 */
	private static String hashThread( String url ) throws IOException, NoSuchAlgorithmException {
		String rawDesc = getFirstPost( url );
		return FTLDat.calcStreamMD5( new ByteArrayInputStream( rawDesc.getBytes( Charset.forName("UTF-8") ) ) );
	}


	/**
	 * Downloads a URL and returns the string content, decoded as UTF-8.
	 */
	private static String fetchWebPage( String url ) throws IOException {
		String result = null;
		InputStream urlIn = null;
		ByteArrayOutputStream bytesOut = null;

		try {
			URLConnection conn = new URL( url ).openConnection();

			if ( conn instanceof HttpURLConnection == false ) {
				throw new MalformedURLException( String.format( "Non-Http(s) URL given to fetch: %s", url ) );
			}
			HttpURLConnection httpConn = (HttpURLConnection)conn;

			httpConn.setReadTimeout( 10000 );
			httpConn.connect();

			int responseCode = httpConn.getResponseCode();

			if ( responseCode == HttpURLConnection.HTTP_OK ) {
				int contentLength = conn.getContentLength();
				urlIn = httpConn.getInputStream();
				bytesOut = new ByteArrayOutputStream( contentLength>0 ? contentLength : 4096 );

				byte[] buf = new byte[4096];
				int len;
				while ( (len = urlIn.read(buf)) >= 0 ) {
					bytesOut.write( buf, 0, len );
				}

				byte[] allBytes = bytesOut.toByteArray();
				CharsetDecoder decoder = Charset.forName( "UTF-8" ).newDecoder();
				ByteBuffer byteBuffer = ByteBuffer.wrap( allBytes, 0, allBytes.length );
				result = decoder.decode( byteBuffer ).toString();
			}
		}
		finally {
			try {if ( urlIn != null ) urlIn.close();}
			catch ( IOException e ) {}

			// No need to close an array stream.
		}

		return result;
	}


	/**
	 * Writes collated catalog entries to a file, as human-editable xml.
	 */
	private static void writeXML( List<ModsInfo> data, File dstFile ) throws IOException, NoSuchAlgorithmException {
		OutputStream os = null;
		try {
			os = new FileOutputStream( dstFile );
			OutputStreamWriter writer = new OutputStreamWriter( os, Charset.forName("US-ASCII") );
			writeXML( data, writer );
			writer.flush();
		}
		finally {
			try {if ( os != null ) os.close();}
			catch ( IOException e ) {}
		}
	}

	private static void writeXML( List<ModsInfo> data, OutputStreamWriter dst ) throws IOException {
		boolean first = true;
		dst.append( "<?xml version=\"1.0\" encoding=\""+ dst.getEncoding() +"\"?>\n" );
		dst.append( "<modsinfoList>\n" );
		for ( ModsInfo modsInfo : data ) {
			if ( !first ) dst.append( "\n" );

			writeXML( modsInfo, dst, "  ", 1 );
			first = false;
		}
		dst.append( "</modsinfoList>" );
	}

	private static void writeXML( ModsInfo modsInfo, OutputStreamWriter dst, String indent, int depth ) throws IOException {
		Format xmlFormat = Format.getPrettyFormat();
		xmlFormat.setEncoding( dst.getEncoding() );
		XMLOutputter xmlOut = new XMLOutputter( xmlFormat );

		writeIndent( dst, indent, depth++ ).append( "<modsinfo>\n" );
		writeIndent( dst, indent, depth ); dst.append("<title>").append( xmlOut.escapeElementEntities( modsInfo.title ) ).append( "</title>\n" );
		writeIndent( dst, indent, depth ); dst.append("<author>").append( xmlOut.escapeElementEntities( modsInfo.author ) ).append( "</author>\n" );
		writeIndent( dst, indent, depth ); dst.append("<threadUrl><![CDATA[ ").append( modsInfo.threadURL ).append( " ]]></threadUrl>\n" );

		writeIndent( dst, indent, depth++ ).append( "<versions>\n" );
		for ( String[] entry : modsInfo.versions ) {
			writeIndent( dst, indent, depth );
			dst.append( "<version hash=\"" ).append( xmlOut.escapeAttributeEntities( entry[0] ) ).append( "\">" );
			dst.append( xmlOut.escapeElementEntities( entry[1] ) );
			dst.append( "</version>" ).append( "\n" );
		}
		writeIndent( dst, indent, --depth ).append( "</versions>\n" );
		writeIndent( dst, indent, depth ); dst.append("<threadHash>").append( modsInfo.threadHash ).append( "</threadHash>\n" );
		dst.append( "\n" );

		writeIndent( dst, indent, depth ); dst.append( "<description>" ).append( "<![CDATA[" );
		dst.append( modsInfo.description );
		dst.append( "]]>\n" );
		writeIndent( dst, indent, depth ); dst.append( "</description>\n" );

		writeIndent( dst, indent, --depth ).append( "</modsinfo>\n" );
	}

	/**
	 * Adds indentation to a given depth.
	 */
	private static Appendable writeIndent( Appendable dst, String indent, int depth ) throws IOException {
		for ( int i=0; i < depth; i++ ) dst.append( indent );
		return dst;
	}


	/**
	 * Parses dumped xml and returns a new catalog.
	 */
	private static ModDB parseCatalogXML( File srcFile ) throws IOException, JDOMException {
		ModDB modDB = new ModDB();
		SAXBuilder builder = new SAXBuilder();
		InputStream is = null;

		try {
			is = new FileInputStream( srcFile );
			Document doc = builder.build( is );
			Element rootNode = doc.getRootElement();


			for ( Element infoNode : rootNode.getChildren( "modsinfo" ) ) {
				String threadURL = infoNode.getChildTextTrim( "threadUrl" );
				String threadHash = infoNode.getChildTextTrim( "threadHash" );

				if ( !threadURL.equals( "???" ) && !threadHash.equals( "???" ) ) {
					String oldHash = modDB.getThreadHash( threadURL );
					if ( oldHash != null && !oldHash.equals( threadHash ) ) {
						log.warn( "Multiple thread hashes for url: "+ threadURL );
					}
					modDB.putThreadHash( threadURL, threadHash );
				}

				for ( Element versionNode : infoNode.getChild( "versions" ).getChildren( "version" ) ) {
					ModInfo modInfo = new ModInfo();
					modInfo.setTitle( infoNode.getChildTextTrim( "title" ) );
					modInfo.setAuthor( infoNode.getChildTextTrim( "author" ) );
					modInfo.setURL( threadURL );
					modInfo.setDescription( infoNode.getChildTextTrim( "description" ) );
					modInfo.setFileHash( versionNode.getAttributeValue( "hash" ) );
					modInfo.setVersion( versionNode.getTextTrim() );
					modDB.addMod( modInfo );
				}
			}
		}
		finally {
			try {if ( is != null ) is.close();}
			catch ( IOException e ) {}
		}

		return modDB;
	}


	/**
	 * Writes collated catalog entries to a file, as condensed json.
	 */
	private static void writeJSON( List<ModsInfo> data, File dstFile ) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = mapper.createObjectNode();

		ObjectNode catalogsNode = rootNode.objectNode();
		rootNode.put( "catalog_versions", catalogsNode );

		ArrayNode catalogNode = rootNode.arrayNode();
		catalogsNode.put( "1", catalogNode );

		for ( ModsInfo modsInfo : data ) {
			ObjectNode infoNode = rootNode.objectNode();
			catalogNode.add( infoNode );

			infoNode.put( "title", modsInfo.title );
			infoNode.put( "author", modsInfo.author );
			infoNode.put( "desc", modsInfo.description );
			infoNode.put( "url", modsInfo.threadURL );

			infoNode.put( "thread_hash", modsInfo.threadHash );

			ArrayNode versionsNode = rootNode.arrayNode();
			infoNode.put( "versions", versionsNode );

			for ( String[] entry : modsInfo.versions ) {
				ObjectNode versionNode = rootNode.objectNode();
				versionNode.put( "hash", entry[0] );
				versionNode.put( "version", entry[1] );
				versionsNode.add( versionNode );
			}
		}

		OutputStream os = null;
		try {
			os = new FileOutputStream( dstFile );
			OutputStreamWriter writer = new OutputStreamWriter( os, Charset.forName("US-ASCII") );
			mapper.writeValue( writer, rootNode );
		}
		finally {
			try {if ( os != null ) os.close();}
			catch ( IOException e ) {}
		}
	}



	/** Information gleaned from scraping the forum. */
	private static class ScrapeResult {
		public String threadURL = null;
		public String title = null;
		public String author = null;
		public boolean wip = false;
		public String rawDesc = null;
		public String threadHash = null;
	}

	/** Combined information from several similar ModInfo objects of varying versions. */
	private static class ModsInfo {
		public String threadURL = null;
		public String title = null;
		public String author = null;
		public String description = null;
		public String threadHash = null;
		public ArrayList<String[]> versions = new ArrayList<String[]>();

		public void putVersion( String fileHash, String fileVersion ) {
			versions.add( new String[] {fileHash, fileVersion} );
		}
	}

	/** A potential commandline option. */
	private static class Option {
		String action;
		String longName;
		String shortName;
		boolean acceptsValue;
		boolean requiresValue;

		public Option( String action, String longName, String shortName, boolean acceptsValue, boolean requiresValue ) {
			this.action = action;
			this.longName = longName;
			this.shortName = shortName;
			this.acceptsValue = acceptsValue;
			this.requiresValue = requiresValue;
		}
	}
}