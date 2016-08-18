package dr.inference.operators;

import dr.evomodel.continuous.FullyConjugateMultivariateTraitLikelihood;
import dr.inference.model.LatentFactorModel;
import dr.inference.model.MatrixParameterInterface;
import dr.math.MathUtils;
import dr.math.distributions.MultivariateNormalDistribution;
import dr.math.matrixAlgebra.Matrix;
import dr.math.matrixAlgebra.SymmetricMatrix;

/**
 * Created by max on 5/16/16.
 */
public class FactorTreeGibbsOperator extends SimpleMCMCOperator implements GibbsOperator{
    LatentFactorModel lfm;
    double pathParameter = 1;
    FullyConjugateMultivariateTraitLikelihood tree;
    FullyConjugateMultivariateTraitLikelihood workingTree;
    MatrixParameterInterface factors;
    MatrixParameterInterface errorPrec;

    public FactorTreeGibbsOperator(double weight, LatentFactorModel lfm, FullyConjugateMultivariateTraitLikelihood tree, FullyConjugateMultivariateTraitLikelihood workingTree){
        setWeight(weight);
        this.tree = tree;
        this.lfm = lfm;
        this.factors = lfm.getFactors();
        errorPrec = lfm.getColumnPrecision();
    }

    @Override
    public int getStepCount() {
        return 0;
    }

    @Override
    public String getPerformanceSuggestion() {
        return null;
    }

    @Override
    public String getOperatorName() {
        return null;
    }

    @Override
    public double doOperation() throws OperatorFailedException {
        int column = MathUtils.nextInt(factors.getColumnDimension());
        MultivariateNormalDistribution mvn = getMVN(column);
        double[] draw = (double[]) mvn.nextRandom();
        for (int i = 0; i < factors.getRowDimension(); i++) {
            factors.setParameterValue(i, column, draw[i]);
        }

        return 0;
    }

    MultivariateNormalDistribution getMVN(int column){
        double[][] precision = getPrecision(column);
        double[] mean = getMean(column, precision);
        return new MultivariateNormalDistribution(mean, precision);
    }

    double[][] getPrecision(int column){
        double [][] treePrec = getTreePrec(column);

        for (int i = 0; i < lfm.getLoadings().getColumnDimension(); i++) {
            for (int j = i; j < lfm.getLoadings().getColumnDimension(); j++) {
                for (int k = 0; k < lfm.getLoadings().getRowDimension(); k++) {
                    treePrec[i][j] += lfm.getLoadings().getParameterValue(k, i) * errorPrec.getParameterValue(k, k) * lfm.getLoadings().getParameterValue(k, j) * pathParameter;
                    treePrec[j][i] = treePrec[i][j];
                }
            }
        }
        return treePrec;
    }

    double[] getMean(int column, double[][] precision){
        Matrix variance = (new SymmetricMatrix(precision)).inverse();
        double[] midMean = new double[lfm.getLoadings().getColumnDimension()];
        double[] condMean = getTreeMean(column);
        double[][] condPrec = getTreePrec(column);
        for (int i = 0; i < midMean.length; i++) {
            for (int j = 0; j < midMean.length; j++) {
                midMean [i] += condPrec[i][j] * condMean[j];
            }
        }
        for (int i = 0; i < lfm.getLoadings().getRowDimension(); i++) {
            for (int j = 0; j < lfm.getLoadings().getColumnDimension(); j++) {
                midMean[j] += lfm.getScaledData().getParameterValue(i, column) * errorPrec.getParameterValue(i,i) * lfm.getLoadings().getParameterValue(i, j) * pathParameter;
            }
        }
        double[] mean = new double[midMean.length];
        for (int i = 0; i < mean.length; i++) {
            for (int j = 0; j < mean.length; j++) {
                mean[i] += variance.component(i, j) * midMean[j];
            }

        }
        return mean;
    }

    public double[][] getTreePrec(int column){
        double[][] answer = tree.getConditionalPrecision(column);
        if(workingTree == null){
            return answer;
        }
        double[][] temp = workingTree.getConditionalPrecision(column);
        for (int i = 0; i <answer.length ; i++) {
            for (int j = 0; j <answer.length ; j++) {
                answer[i][j] = answer[i][j] * pathParameter + temp[i][j] * (1 - pathParameter);
            }

        }
        return answer;
    }

    public double[] getTreeMean(int column){
        double[] answer = tree.getConditionalMean(column);
        if(workingTree == null){
            return answer;
        }
        double[] temp = workingTree.getConditionalMean(column);
        for (int i = 0; i <answer.length ; i++) {
                answer[i] = answer[i] * pathParameter + temp[i] * (1 - pathParameter);
            }
        return answer;
    }

    @Override
    public void setPathParameter(double beta) {
        pathParameter = beta;
    }
}
