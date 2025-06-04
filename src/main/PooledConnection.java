import java.sql.*;

//metodo que representara la instancia de los hilos unidos al pool de conexiones

public class PooledConnection implements Connection {

    //conexion fisica/real a SQL
    private final Connection realConnection;
    //instancia de pool para usar metodos
    private final PoolDeConexiones pool;

    //booleano para verificar si una conex esta cerrada o no
    private boolean isClosed = false;

    //constructor de la clase 
    public PooledConnection(Connection realConnection, PoolDeConexiones pool) {
        this.realConnection = realConnection;
        this.pool = pool;
    }

    //metodo para cerrar la conexion real (fisica)

    @Override
    public void close() throws SQLException {
        if (!isClosed) {
            //si no esta cerrado cambia status a cerrado
            isClosed = true;
            pool.devolverConexionFisica(realConnection);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        //si la conexion esta cerrada indica que la conexion se cerro modificando el metodo createStatement con @Override
        if (isClosed) throw new SQLException("Closed Connection");
        return realConnection.createStatement();
    }

    @Override 
    public boolean isClosed() {
        //verifica el estado de la conexion (cerrada/abierta)
        return isClosed;
    }

}