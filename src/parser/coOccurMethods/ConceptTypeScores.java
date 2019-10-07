package parser.coOccurMethods;

import catalog.Quantity;
import iitb.shared.EntryWithScore;

import java.util.List;

public interface ConceptTypeScores {
	public enum ConceptClassifierTypes {perfectMatch, cooccur, classifier};
	public List<EntryWithScore<Quantity>> getConceptScores(String hdr)
			throws Exception;

}