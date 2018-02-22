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

        return <div><h2>Vendors</h2>
            <VendorsTable vendors={vendors} />
        </div>
    }
}

export default Vendors;
