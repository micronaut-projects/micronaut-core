import React, {Component} from 'react';
import VendorsTable from "./VendorsTable";
import config from '../config/index'

class Vendors extends Component {

    constructor() {
        super();

        this.state = {
            vendors: []
        }
    }

    componentDidMount(){


        fetch(`${config.SERVER_URL}/vendors`)
            .then(r => r.json())
            .then(json => this.setState({vendors: json}))
            .catch(e => console.error(e))
    }

    render() {
        const {vendors} = this.state;

        return <div>
          <div className="jumbotron jumbotron-fluid">
            <div className="container">
              <h1 className="display-4">Vendors</h1>
            </div>
          </div>
            <VendorsTable vendors={vendors} />
        </div>
    }
}

export default Vendors;
