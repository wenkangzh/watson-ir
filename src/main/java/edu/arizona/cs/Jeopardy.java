/**
*	Wenkang Zhou
*	CSc 583
* 	Project Jeopardy
* 	
*/
package edu.arizona.cs;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;

import java.io.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.FSDirectory;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.util.Arrays;
import java.io.IOException;
import java.util.Scanner;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.net.URL;
import java.util.regex.Pattern;
import edu.stanford.nlp.simple.*;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.util.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.*;
import opennlp.tools.stemmer.PorterStemmer;
import java.util.Comparator;
import java.util.Collections;


public class Jeopardy
{

	public static void main(String[] args) throws IOException, ParseException
	{
		String indexFilePath = args[0];
		String indexOrNot = args[1];
		if(indexOrNot.equals("yes"))
			indexing(indexFilePath);
		// searchWithQuery(indexFilePath, "Title residence of Otter, Flounder, Pinto & Bluto in a 1978 comedy");
		searchWithFileOfQuestions(indexFilePath);
		// System.out.println(stemmingSentence("This blonde beauty who reprised her role as Amanda on the new was a psychology major"));
	}

	public static void indexing(String indexFilePath) throws IOException, ParseException
	{
		System.out.println("indexing...");
		// get class loader
		Jeopardy obj = new Jeopardy();
		ClassLoader classLoader = obj.getClass().getClassLoader();
		// the FSDirectory file
		WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
		Directory indexDirectory = FSDirectory.open(Paths.get(indexFilePath));
		IndexWriter w = new IndexWriter(indexDirectory, indexWriterConfig);

		// the wiki files
		File f = new File(classLoader.getResource("wiki").getFile());
		File[] wikiFiles = f.listFiles();
		for (int i = 0;i < wikiFiles.length;i++ ) {
			processOneFile(w, wikiFiles[i]);
		}
		w.close();
	}

	private static void processOneFile(IndexWriter w, File file) throws IOException {
		Scanner in = new Scanner(file);
		String docBuffer = "";		// for buffering a document
		String currDocTitle = "";
		while(in.hasNextLine()){
			String line = in.nextLine();
			if(line.contains("[[") && line.contains("]]")){
				// new document title detected.
				// end with the last doc and start a new document 
				String newDocTitle = (line.split(Pattern.quote("[["))[1]).split(Pattern.quote("]]"))[0];
				if(currDocTitle.compareTo("") != 0){
					// need to add the last doc in the index
					// System.out.println(currDocTitle);
					addDoc(w, docBuffer, currDocTitle);
				}
				// now turn to the new doc
				currDocTitle = newDocTitle;
				docBuffer = "";
			}
			else{
				if(line.contains("[tpl]") && line.contains("[/tpl]")){
					// need to remove the tag
					line = removetpl(line);
				}
				if(line.contains("=="))
					line = line.replaceAll(Pattern.quote("=="), "");
				// add current line to the buffer
				docBuffer += lemmatize(line);
			}
			// process each line HERE.
			// System.out.println(line);
		}
		// need to add the last one in buffer to the index
		addDoc(w, docBuffer, currDocTitle);
	}

	private static String stemmingSentence(String str){
		PorterStemmer stemmer = new PorterStemmer();
		String[] splits = str.split(" ");
		String result = "";
		for(int i = 0; i < splits.length; i++)
			splits[i] = stemmer.stem(splits[i]);
		return String.join(" ", splits);
	}

	private static String lemmatize(String str){
		edu.stanford.nlp.simple.Document d = new edu.stanford.nlp.simple.Document(str);
		ArrayList<String> lemmas = new ArrayList<>();
		for(Sentence s : d.sentences()) {
        	lemmas.addAll(s.lemmas());
        }
        return String.join(" ", lemmas);
	}

	private static String removetpl(String str){
		str = " " + str + " ";
		while(str.contains("[tpl]") && str.contains("[/tpl]")){
			String[] temp = str.split(Pattern.quote("[tpl]"));
			String left = temp[0];
			String right_temp = String.join("[tpl]", Arrays.copyOfRange(temp, 1, temp.length));
			String[] temp2 = right_temp.split(Pattern.quote("[/tpl]"));
			String right = String.join("[/tpl]", Arrays.copyOfRange(temp2, 1, temp2.length));
			str = left + right;
		}
		return str.trim();
	}

	private static void addDoc(IndexWriter w, String wikiDoc, String wikiTitle) throws IOException {
		Document doc = new Document();
		doc.add(new TextField("wikiDoc", wikiDoc, Field.Store.YES));
		doc.add(new StringField("wikiTitle", wikiTitle, Field.Store.YES));
		w.addDocument(doc);
	}


	public static void searchWithFileOfQuestions(String indexFilePath) throws IOException, ParseException 
	{
		Jeopardy obj = new Jeopardy();
		ClassLoader classLoader = obj.getClass().getClassLoader();
		File questionsFile = new File(classLoader.getResource("questions.txt").getFile());
		Scanner in = new Scanner(questionsFile);
		String category = "";
		String question = "";
		String answer = "";
		int scannerCounter = 0;		// used for the scanner
		int questionCounter = 0;	// used to count the total number of questions, for evaluation
		int correctQ = 0;			// used to count the number of questions that answered correctly by the system.
		while(in.hasNextLine()){
			String line = in.nextLine().trim();
			System.out.println(line);
			switch (scannerCounter) {
				case 0:
					category = line;
					scannerCounter++;
					break;
				case 1:
					question = line;
					scannerCounter++;
					break;
				case 2:
					answer = line;
					scannerCounter++;
					break;
				case 3:
					// do the searching at the blank line.
					String queryStr = question + " " + category;
					// System.out.println(category + "\n" + question + "\n" + answer);
					String result = searchWithQuery(indexFilePath, queryStr).trim();
					System.out.println("---------------------------------------------");
					if(answer.contains("|")){
						String[] answers = answer.split(Pattern.quote("|"));
						List<String> list = Arrays.asList(answers);
						if(list.contains(result))
							correctQ++;
					}
					else if(result.compareTo(answer) == 0)
						correctQ++;
					questionCounter++;
					scannerCounter = 0;
					break;
				default:
					System.out.println("Not going to happen here.");
			}
		}
		System.out.println("===================");
		System.out.println(correctQ + "/" + questionCounter);
	}

	private static String searchWithQuery(String indexFilePath, String queryString) throws IOException, ParseException 
	{
		WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
		Query q = new QueryParser("wikiDoc", analyzer).parse(QueryParser.escape(lemmatize(queryString)));

		Directory indexDirectory = FSDirectory.open(Paths.get(indexFilePath));
		IndexReader reader = DirectoryReader.open(indexDirectory);
		IndexSearcher searcher = new IndexSearcher(reader);
		searcher.setSimilarity(new ClassicSimilarity());
		TopDocs docs = searcher.search(q, 20);
		ScoreDoc[] hits = docs.scoreDocs;

		// hits = languageModelProcess(searcher, hits, queryString);

		String finalAns = "";

		System.out.println("Found " + hits.length + " hits.");
		for(int i=0;i<hits.length;++i) {
		    int docIndex = hits[i].doc;
		    Document d = searcher.doc(docIndex);
		    System.out.println((i + 1) + ". " + d.get("wikiTitle") + " :: " + hits[i].score);
		    if(i == 0)
		    	finalAns = d.get("wikiTitle");
		}
		return finalAns;
	}

	private static ScoreDoc[] languageModelProcess(IndexSearcher searcher, ScoreDoc[] inputDocs, String queryString) throws IOException {
		ArrayList<LanguageModelDocument> docs = new ArrayList<>();
		String[] query = lemmatize(queryString).split(" ");
		for(int i = 0; i < inputDocs.length;i++){
			int docIndex = inputDocs[i].doc;
			Document doc = searcher.doc(docIndex);
			String wikiTitle = doc.get("wikiTitle");
			String wikiContent = doc.get("wikiDoc");
			ArrayList<String> lemmas = new ArrayList<>(Arrays.asList(wikiContent.split(" ")));
			double languageModelScore = 1;
			for(String t : query){
				int tf = Collections.frequency(lemmas, t);
				int ld = lemmas.size();
				int T = 0;			// total number of tokens in the collection, for smoothing use
				int cf = 0;			// the number of occu
				for(ScoreDoc tempD : inputDocs){
					int j = tempD.doc;
					Document docTemp = searcher.doc(j);
					String wikiContentTemp = docTemp.get("wikiDoc");
					ArrayList<String> temp = new ArrayList<>(Arrays.asList(wikiContentTemp.split(" ")));
					T += temp.size();
					cf += Collections.frequency(temp, t);
				}
				double PtMc = (double) cf / T;
				double parameter = ((double) tf + 0.1 * PtMc) / (ld + 0.1);
				languageModelScore *= parameter;

			}
			inputDocs[i].score = (float) languageModelScore;
			docs.add(new LanguageModelDocument(docIndex, languageModelScore));
		}
		
		Collections.sort(docs);
		// Collections.reverse(docs);
		for(int i = 0; i < inputDocs.length; i++){
			inputDocs[i].doc = docs.get(i).index;
			inputDocs[i].score = (float) docs.get(i).score;
		}
		return inputDocs;
	}

}


class LanguageModelDocument implements Comparable<LanguageModelDocument>{
	int index;
	// String wikiTitle;
	double score;

	public LanguageModelDocument(int index, double score){
		this.index = index;

		this.score = score;
	}

	@Override
	public int compareTo(LanguageModelDocument o){
		return new Double(score).compareTo(o.score);
	}
}


