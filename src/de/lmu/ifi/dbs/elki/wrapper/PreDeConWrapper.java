package de.lmu.ifi.dbs.elki.wrapper;

import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.clustering.subspace.PreDeCon;
import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.utilities.Util;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.PatternParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;

/**
 * A wrapper for the PreDeCon algorithm. Performs an attribute wise normalization on
 * the database objects.
 *
 * @author Elke Achtert
 */
public class PreDeConWrapper<O extends DatabaseObject> extends NormalizationWrapper<O> {

    /**
     * Parameter to specify the maximum radius of the neighborhood to be considered,
     * must be suitable to {@link de.lmu.ifi.dbs.elki.distance.distancefunction.LocallyWeightedDistanceFunction}.
     * <p>Key: {@code -projdbscan.epsilon} </p>
     */
    private final PatternParameter EPSILON_PARAM = new PatternParameter(PreDeCon.EPSILON_ID);

    /**
     * Parameter to specify the threshold for minimum number of points in
     * the epsilon-neighborhood of a point,
     * must be an integer greater than 0.
     * <p>Key: {@code -projdbscan.minpts} </p>
     */
    private final IntParameter MINPTS_PARAM = new IntParameter(
        PreDeCon.MINPTS_ID,
        new GreaterConstraint(0));

    /**
     * Parameter to specify the intrinsic dimensionality of the clusters to find,
     * must be an integer greater than 0.
     * <p>Key: {@code -projdbscan.lambda} </p>
     */
    private final IntParameter LAMBDA_PARAM = new IntParameter(
        PreDeCon.LAMBDA_ID,
        new GreaterConstraint(0));

    /**
     * Main method to run this wrapper.
     *
     * @param args the arguments to run this wrapper
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        new PreDeConWrapper().runCLIWrapper(args);
    }

    /**
     * Adds parameters
     * {@link #EPSILON_PARAM}, {@link #MINPTS_PARAM} and {@link #LAMBDA_PARAM}
     * to the option handler additionally to parameters of super class.
     */
    public PreDeConWrapper() {
        super();
        // parameter epsilon
        addOption(EPSILON_PARAM);

        // parameter min points
        addOption(MINPTS_PARAM);

        // parameter lambda
        addOption(LAMBDA_PARAM);
    }

    @Override
    public List<String> getKDDTaskParameters() {
        List<String> parameters = super.getKDDTaskParameters();

        // PreDeCon algorithm
        Util.addParameter(parameters, OptionID.ALGORITHM, PreDeCon.class.getName());

        // epsilon for PreDeCon
        Util.addParameter(parameters, EPSILON_PARAM, getParameterValue(EPSILON_PARAM));

        // minpts for PreDeCon
        Util.addParameter(parameters, MINPTS_PARAM, Integer.toString(getParameterValue(MINPTS_PARAM)));

        // lambda for PreDeCon
        Util.addParameter(parameters, LAMBDA_PARAM, Integer.toString(getParameterValue(LAMBDA_PARAM)));

        return parameters;
    }
}
