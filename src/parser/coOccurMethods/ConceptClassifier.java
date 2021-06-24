package parser.coOccurMethods;

import catalog.*;
import de.bwaldvogel.liblinear.Linear;
import gnu.trove.list.array.TIntArrayList;
import iitb.shared.EntryWithScore;
import iitb.shared.StringMap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Element;
import parser.*;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.LibLINEAR;
import weka.core.*;
import weka.core.converters.ArffLoader.ArffReader;

import java.io.*;
import java.util.*;

public class ConceptClassifier implements ConceptTypeScores,Co_occurrenceScores, Serializable {
  private static final int FreqCutOff = 10;
  public static final String ClassifierFile = "conceptClassifier";
  public static final double UndecidedScore = 0.3;
  public static final double MinScore = 0.01;
  public static String[][] ignoredConcepts = {};//{"Percent","%"}};
  static Hashtable<String,String[]> ignoredConceptsHash=new Hashtable<String,String[]>();
  StringMap<String> wordIdMap = new StringMap<String>();
  TIntArrayList wordFreqs = new TIntArrayList();
  //StringMap<String> classIdMap = new StringMap<String>();
  TIntArrayList featureIds = new TIntArrayList();
  ArrayList<String> instClassLabels = new ArrayList<String>();
  ArrayList<String> hdrs = null; //new ArrayList<String>();
  TIntArrayList offsets = new TIntArrayList();
  List<Quantity> concepts;
  boolean addInst = false;
  MyClassifier myclassifier = new MyClassifier();
  private SimpleParser parser;
  transient CFGParser4Header cfgparser;
  SparseInstance emptyInst;
  QuantityCatalog quantDict;
  public ConceptClassifier(QuantityCatalog quantDict) throws Exception {
    this(null,quantDict,null,null);
  }
  public ConceptClassifier(Element configs, QuantityCatalog quantDict, boolean trainMode, SimpleParser parser) throws Exception {
    configs = QuantityCatalog.loadDefaultConfig(configs);
    this.quantDict = quantDict;
    this.concepts = quantDict.getQuantities();
    this.parser = parser;
    if (parser == null) this.parser = new RuleBasedParser(configs, quantDict,this);
    if (trainMode) cfgparser = new CFGParser4Header(null,quantDict);
    init();
  }
  public ConceptClassifier(Co_occurrenceStatistics coOccurStats) throws Exception {
    this(coOccurStats.quantityDict,null);
  }
  private void init() {
    for (int i = 0; i < ignoredConcepts.length; i++) {
      for (int j = 0; j < ignoredConcepts.length; j++)
        ignoredConceptsHash.put(ignoredConcepts[i][j],ignoredConcepts[i]);
    }
  }
  public ConceptClassifier(QuantityCatalog quantDict, String loadFile) throws Exception {
    this(null,quantDict,null,loadFile);
  }
  public ConceptClassifier(Element configs, QuantityCatalog quantDict, SimpleParser parser,
      String loadFile) throws Exception {
    this(configs,quantDict,false,parser);
    InputStream istr=null;

    loadFile = "unit_tagger_data/conceptClassifier";

    if (loadFile==null) {
      istr = QuantityCatalog.class.getClassLoader().getResourceAsStream(ConceptClassifier.ClassifierFile);
    } else {
      istr = new FileInputStream(loadFile);
    }
    myclassifier = (MyClassifier) weka.core.SerializationHelper.read(istr);
    myclassifier.classifier.setDoNotReplaceMissingValues(true);
    formEmptyInstance(emptyDataset());

  }
  private void formEmptyInstance(Instances dataset) {
    emptyInst = new SparseInstance(myclassifier.wordIdMap.size());
    for(int f = 0; f < emptyInst.numAttributes(); f++)
      emptyInst.setValue(f, 0);
    emptyInst.setDataset(dataset);
  }
  public static class MySparseInstance extends SparseInstance {
    @Override
    public Object copy() {
      Instance result = new MySparseInstance(this);
      result.setDataset(dataset());
      return result;
    }
    String hdr;
    public MySparseInstance(SparseInstance emptyInst, String hdr) {
      super(emptyInst);
      this.hdr = hdr;
    }
    public MySparseInstance(MySparseInstance mySparseInstance) {
      this(mySparseInstance,mySparseInstance.hdr);
    }
  }
  public static class MyClassifier implements Serializable {
    LibLINEAR classifier;
    StringMap<String> wordIdMap = new StringMap<String>();

  }
  private Instances formInstances(String arffFile) throws IOException {
    for (int i = 0; i < wordIdMap.size(); i++) {
      if (featureSelected(i)) {
        myclassifier.wordIdMap.add(wordIdMap.get(i));
      }
    }
    Instances dataset = emptyDataset(); 
    int numFs = myclassifier.wordIdMap.size();
    formEmptyInstance(dataset);
    offsets.add(featureIds.size());
    int numInsts = instClassLabels.size();
    for (int i = 0; i < numInsts; i++) {
      SparseInstance inst = new MySparseInstance(emptyInst,hdrs==null?null:hdrs.get(i));
      for (int f = offsets.get(i); f < offsets.get(i+1); f++) {
        int fid = featureIds.get(f);
        int attrId = myclassifier.wordIdMap.get(wordIdMap.get(fid));
        if (attrId >= 0) inst.setValue(attrId, 1);
      }
      if (inst.numValues() > 0) {
        inst.setDataset(dataset);
        inst.setClassValue(instClassLabels.get(i));
        dataset.add(inst);
      }
    }
    if (arffFile != null) {
      BufferedWriter writer = new BufferedWriter(new FileWriter(arffFile));
      writer.write(dataset.toString());
      writer.flush();
      writer.close();
    }
    return dataset;
  }
  public void makeClassifier(String trainFile, boolean createArffFile) throws Exception {
    myclassifier = new MyClassifier();
    Instances dataset = createArffFile?formInstances(trainFile):readTrainFile(myclassifier,trainFile);
    LibLINEAR classifier = new LibLINEAR();
    classifier.setDoNotReplaceMissingValues(true);
    myclassifier.classifier = classifier;
    String classifierOptions="-S:0:-P";
    ((OptionHandler)classifier).setOptions(classifierOptions.split(":"));
    int numFolds = 3;
    dataset.randomize(new Random(1));
    for (int f = 0; f < numFolds; f++) {
      classifier.buildClassifier(dataset.trainCV(numFolds, f));
      System.out.println(classifier.toString());
      System.out.println(((LibLINEAR)classifier).globalInfo());
      Evaluation eval = new Evaluation(dataset);
      //eval.crossValidateModel(classifier, dataset, 3, new Random(1));
      Instances testData =  dataset.testCV(numFolds, f);
      double pred[] = eval.evaluateModel(classifier,testData);
      System.out.println(eval.toSummaryString()+" "+eval.toMatrixString());
    }
    classifier.buildClassifier(dataset);
    System.out.println(classifier.toString());

    weka.core.SerializationHelper.write(ClassifierFile, myclassifier);
    if (hdrs != null) {
      Evaluation eval = new Evaluation(dataset);
      double pred[] = eval.evaluateModel(classifier,dataset);
      System.out.println(eval.toSummaryString()+" "+eval.toMatrixString());
      for (int i = 0; i < pred.length; i++) {
        int trueClass = (int) dataset.instance(i).classValue();
        int predClass = (int) pred[i];
        if (trueClass != predClass) {
          printInstance((MySparseInstance) dataset.instance(i),trueClass,predClass);
        }
      }
    }
  }
  private Instances readTrainFile(MyClassifier myclassifier, String trainFile) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(trainFile));
    ArffReader arff = new ArffReader(reader);
    Instances data = arff.getData();
    data.setClassIndex(data.numAttributes() - 1);
    for (int i = 0; i < data.numAttributes()-1; i++) {
      myclassifier.wordIdMap.add(data.attribute(i).name());
    }
    for (int i = 0; i < data.numInstances(); i++) {
      assert (!data.get(i).classIsMissing());
      if (data.get(i).classValue()==0) {
        for (int j = 0; j < data.get(i).numAttributes(); j++)
          if (!data.get(i).isMissing(j) && data.get(i).value(j) > 0) System.out.print(data.get(i).attribute(j).name()+ " ");
        System.out.println(data.get(i));
      }
    }
    formEmptyInstance(data);
    return data;
  }
  private Instances emptyDataset() {
    int numFs = myclassifier.wordIdMap.size();
    ArrayList<Attribute> attributes = new ArrayList<Attribute>(numFs+1);
    for (int i = 0; i < numFs; i++) {
      attributes.add(new weka.core.Attribute(myclassifier.wordIdMap.get(i)));
    }
    FastVector classNames = new FastVector();
    for (int i = 0; i < concepts.size(); i++) {
      classNames.addElement(getClassString(i));
    }
    attributes.add(new weka.core.Attribute("Class",classNames));
    int numInsts = instClassLabels.size();
    Instances dataset = new Instances("ConceptClassifer",attributes,numInsts);
    dataset.setClassIndex(numFs);
    return dataset;
  }
  private void printInstance(MySparseInstance instance, int trueClass, int predClass) {
    System.out.print(instance.hdr+ " tokens=");
    for (int i = 0; i < instance.numValues(); i++) {
      int attInd = instance.attributeSparse(i).index();
      if (attInd < instance.numAttributes()) System.out.print(wordIdMap.get(attInd)+ " ");
    }
    System.out.println(getClassString(trueClass)+ " "+getClassString(predClass));
  }
  private boolean featureSelected(int fid) {
    return (wordFreqs.get(fid) > FreqCutOff);
  }
  public void appendInstanceFeature(String token) {
    if (addInst) {
      int f = wordIdMap.add(token);
      featureIds.add(f);
      if (f < wordFreqs.size()) {
        wordFreqs.setQuick(f, wordFreqs.getQuick(f)+1);
      } else {
        wordFreqs.add(1);
      }
    }
  }
  public String addNewInstance(String concept, String hdr) {
    if (ignoredConceptsHash.containsKey(concept)) {
      addInst=false;
      return null;
    }
    addInst=true;
    offsets.add(featureIds.size());
    instClassLabels.add(concept);
    //classIdMap.add(concept);
    if (hdrs != null) hdrs.add(hdr);
    return concept;
  }

  // use rule-based parser to find high precision unit matches.
  public String addHeader(String hdr, List<String> tokens, UnitSpan unitSpan) throws IOException {
    int start = unitSpan.start();
    int end = unitSpan.end();
    Unit unit = unitSpan.getKey();

    String classLabel = addNewInstance(unit.getParentQuantity().getConcept(),hdr);
    boolean addedTok = false;
    if (classLabel != null) {
      for (int t = 0; t < tokens.size(); t++) {
        if (t >= start && t <= end) continue;
        if (WordnetFrequency.stopWordsHash.contains(tokens.get(t))) continue;
        //if (!checkTokensCorrectness(tokens.get(t))) return false;
        if (tokens.get(t).length()>1 && Character.isLetter(tokens.get(t).charAt(0))) {
          appendInstanceFeature(tokens.get(t));
          addedTok = true;
        }
      }
    }
    return addedTok?classLabel:null;
  }
  public String  addHeader(String hdr, Vector<String> explanation) throws IOException {
    ParseState[] hdrMatches = new ParseState[1];
    List<? extends EntryWithScore<Unit>> units = parser.parseHeaderExplain(hdr, explanation, 0, hdrMatches);
    List<String> tokens = hdrMatches[0].tokens;
    if (tokens == null) return null;
    if ((units == null || units.size()==0) && explanation.size()>0)
      return null;

    if (explanation.size() == 0) {
      units = cfgparser.parseHeader(hdr, hdrMatches[0],0,null,null,1, null);
      if (units == null || units.size()!=1) 
        return null; 
      if (units.get(0).getKey().getParentQuantity() == null || !units.get(0).getKey().getParentQuantity().getConcept().equals("Currency")) {
        return null;
      }
    }
    UnitSpan unitSpan = (UnitSpan) units.get(0);
    return addHeader(hdr,tokens, unitSpan);
    //if (unitSpan.getKey().getParentQuantity().getConcept().equalsIgnoreCase("Multiples")) {
    //	System.out.println(hdr);
    //}
  }
  /* (non-Javadoc)
   * @see parser.coOccurMethods.ConceptTypeScores#getConceptScores(java.lang.String)
   */
  @Override
  public List<EntryWithScore<Quantity>> getConceptScores(String hdr) throws Exception {
    return getConceptScores(hdr, null);
  }
  public List<Pair<String,Double>> predictedQuantityTypes(String hdr) throws Exception {
    List<EntryWithScore<Quantity>> qtypes = getConceptScores(hdr,null);
    if (qtypes == null || qtypes.size() == 0) {
      return null;
    }
    List<Pair<String, Double>> typePredictions = new Vector<Pair<String, Double>>();
    for (EntryWithScore<Quantity> qtype : qtypes) {
      typePredictions.add(new ImmutablePair<String, Double>(qtype.getKey().getConcept(), qtype.getScore()));
    }
    return typePredictions;
  }
  // concept, score map.
  public List<EntryWithScore<Quantity>> getConceptScores(String hdr, String predLabel[]) throws Exception {
    ParseState[] hdrMatches = new ParseState[1];
    Vector<String> explanation = new Vector<String>();
    List<? extends EntryWithScore<Unit>> units = parser.parseHeaderExplain(hdr, explanation, 0, hdrMatches);

    if (units!=null&&units.size()==1&&explanation.size()==1) {
      return QuantityCatalog.newList(units.get(0).getKey().getParentQuantity(),1);
    }
    /*
		for (int t = tokens.size()-1; t >= 0; t--) {
			String tok = tokens.get(t);
			if (ignoredConceptsHash.containsKey(tok)) {

			}
		}
     */

    return getConceptScores(hdrMatches[0].setTokens(),predLabel);
  }
  public List<EntryWithScore<Quantity>> getConceptScores(List<String> tokens, String predLabel[]) throws Exception {
    SparseInstance inst = null;
    List<EntryWithScore<Quantity>> conceptScore = null;
    for (int t = tokens.size()-1; t >= 0; t--) {
      String tok = tokens.get(t);
      int id = myclassifier.wordIdMap.get(tok);
      if (id < 0) continue;
      if (inst == null) {
        inst = new SparseInstance(emptyInst);
        inst.setDataset(emptyInst.dataset());
      }
      inst.setValue(id, 1);
    }
    if (inst != null) {
      //System.out.println(inst.toString());
      double dist[] = myclassifier.classifier.distributionForInstance(inst);
      if (predLabel != null) {
        int classId = (int) myclassifier.classifier.classifyInstance(inst);
        predLabel[0] = getClassString(classId);
      }
      for (int i = 0; i < dist.length; i++) {
        if (dist[i] > MinScore) {
          if (conceptScore == null) 
            conceptScore = new Vector<EntryWithScore<Quantity>>();
          conceptScore.add(new EntryWithScore<Quantity>(getClassQuant(i),dist[i]));
        }
      }
    }
    if (conceptScore != null) {
      Collections.sort(conceptScore);
      if (conceptScore.size()==0 || conceptScore.get(0).getScore() < UndecidedScore) {
        Quantity multQuant = quantDict.multipleOneUnit().getParentQuantity();
        boolean multPresent = false;

        for (EntryWithScore<Quantity> entry : conceptScore) {
          if (entry.getKey()==multQuant) {
            entry.setScore(Math.max(entry.getScore(), UndecidedScore));
            multPresent = true;
            break;
          }
        }
        if (!multPresent) {
          conceptScore.add(new EntryWithScore<Quantity>(multQuant, UndecidedScore));
          for (EntryWithScore<Quantity> entry : conceptScore) {
            entry.setScore(entry.getScore()*(1-UndecidedScore));
          }
        }
        Collections.sort(conceptScore);
      }
    }
    return conceptScore;
  }
  private String getClassString(int i) {
    return concepts.get(i).getConcept();
  }
  private Quantity getClassQuant(int i) {
    return concepts.get(i);
  }
  @Override
  public float[] getCo_occurScores(List<String> hdrToks, StringMap<Unit> units) {
    List<EntryWithScore<Quantity>> scores=null;
    try {
      scores = getConceptScores(hdrToks,null);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if (scores == null || scores.size()==0) return null;
    float totalScores[] = new float[units.size()];
    for (int i = 0; i < totalScores.length; i++) {
      Quantity q = units.get(i).getParentQuantity();
      for (int j = 0; j < scores.size(); j++) {
        if (q == scores.get(j).getKey()) {
          totalScores[i] = (float) scores.get(j).getScore();
        }
      }
    }
    return totalScores;
  }
  @Override
  public boolean adjustFrequency() {
    return true;
  }
  @Override
  public float freqAdjustedScore(float freq, float f) {
    return freq*f;
  }
  /**
   * @param args
   * @throws Exception 
   */
  public static void main(String[] args) throws Exception {
    Linear.resetRandom();
    String dir = "data/tableHeaders/";///mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/";
    String paths[]={
        "PercentSymbolMatch",
        "statParsed"
        , "SingleUnitAfterIn"
        , "DictConceptMatch1Unit"
        , "SingleUnitWithinBrackets"
        , "PercentSymbolMatch"
    };
    float sampleRates[] = {0.07f,1,1,1,1};
    Element elem = QuantityCatalog.loadDefaultConfig(null);
    QuantityCatalog quantDict = new QuantityCatalog(elem);
    ConceptClassifier classifier = null;
    String trainFile = QuantityCatalog.QuantConfigDirPath+ConceptClassifier.ClassifierFile+".arff";
    if (args.length > 0 && args[0].equalsIgnoreCase("train")) {
      classifier = new ConceptClassifier(elem,quantDict,false,null);
      classifier.makeClassifier(trainFile, false);
    } else if (args.length > 0 && args[0].equalsIgnoreCase("parse-train")) {
      classifier = new ConceptClassifier(null,quantDict,true,null);
      Co_occurrenceStatistics coOccur = new Co_occurrenceStatistics(quantDict);
      Vector<String> explanation = new Vector<String>();
      Random random = new Random(1);
      for (int i = 0; i < paths.length; i++) {
        for (int j = 0; j < 10; j++) {
          File file = new File(dir + paths[i]+j);
          if (!file.exists()) {
            System.out.println("did not find..."+dir + paths[i]+j);
            continue;
          }
          System.out.println("reading..."+dir + paths[i]+j);
          BufferedReader reader = new BufferedReader(new FileReader(file));
          String line;
          while ((line = reader.readLine())!= null) {
            if (random.nextDouble()>sampleRates[i]) continue;
            line = line.trim();
            if (!line.startsWith("<h>")) continue;
            String hdr = line.substring(3,line.indexOf("</h>")).trim();
            classifier.addHeader(hdr, explanation);
            coOccur.addHeader(hdr, explanation);
          }
        }
      }
    // classifier.makeClassifier(trainFile,true);
    } else {
      classifier = new ConceptClassifier(quantDict); //,parser,
    }
    String conceptTests[] = {"umemployment rate", "rate", "dose", "corporate income tax rate", "area code", "forest area", "Urban Area Population", "area 1000 sq km", "area", "area sq", "area km", "CO2 emissions", "distance from sun","net worth","year of first flight","weight", "pressure", "record low", "size", "volume","bandwidth","capacity"};
    for (String hdr : conceptTests) {
      System.out.print(hdr);
      List<String> tokens = QuantityCatalog.getTokens(hdr);
      List<EntryWithScore<Quantity>> scores = classifier.getConceptScores(hdr);
      if (scores != null) {
        int cnt = 0;
        for (Iterator<EntryWithScore<Quantity>> iter = scores.iterator(); iter.hasNext();cnt++) {
          EntryWithScore<Quantity> entry = iter.next();
          System.out.print("\t"+entry.getKey().getConcept()+ " "+entry.getScore());
        }
        System.out.println();
        /*
				for (String tok : tokens) {
					float freq[] = coOccur.getConceptFrequencies(tok); 
					System.out.print(tok);
					for (int i = 0; i < freq.length; i++) {
						if (freq[i] > 0) System.out.print(" "+quantDict.getQuantities().get(i).getConcept()+ " "+freq[i]);
					}
					System.out.println();
				}
         */
      }
    }

  }

}
